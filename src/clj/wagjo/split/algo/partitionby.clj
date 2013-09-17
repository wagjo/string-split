;; Copyright (C) 2013, Jozef Wagner. All rights reserved.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0
;; (http://opensource.org/licenses/eclipse-1.0.php) which can be
;; found in the file epl-v10.html at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound
;; by the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns wagjo.split.algo.partitionby
  "Generic reducible and foldable partition-by."
  (:refer-clojure :exclude [partition-by remove])
  (:require [clojure.core.reducers :as r]
            [wagjo.util.thread-last :as ->>]))

;;;; Implementation details

(def ^:private nothing (Object.))

(defn ^:private nothing?
  "Returns true if object is identical to nothing."
  [o]
  (identical? nothing o))

(defn ^:private dered
  "Returns dereferenced x, if x is reducible. Returns x otherwise."
  [x]
  (if (reduced? x) @x x))

(defprotocol IUnsynchronizedRef
  (get-val! [_])
  (set-val! [_ new-val]))

(deftype UnsynchronizedRef [^:unsynchronized-mutable val]
  IUnsynchronizedRef
  (get-val! [_] val)
  (set-val! [_ new-val] (set! val new-val)))

(defprotocol IUnsynchronizedPart
  (get-before! [_])
  (set-before! [_ new-val])
  (get-before-fval! [_])
  (set-before-fval! [_ new-val])
  (get-ret! [_])
  (set-ret! [_ new-val])
  (get-after! [_])
  (set-after! [_ new-val])
  (get-after-fval! [_])
  (set-after-fval! [_ new-val]))

(deftype UnsynchronizedPart [^:unsynchronized-mutable before
                             ^:unsynchronized-mutable before-fval
                             ^:unsynchronized-mutable ret
                             ^:unsynchronized-mutable after
                             ^:unsynchronized-mutable after-fval]
  IUnsynchronizedPart
  (get-before! [_] before)
  (set-before! [_ new-val] (set! before new-val))
  (get-before-fval! [_] before-fval)
  (set-before-fval! [_ new-val] (set! before-fval new-val))
  (get-ret! [_] ret)
  (set-ret! [_ new-val] (set! ret new-val))
  (get-after! [_] after)
  (set-after! [_ new-val] (set! after new-val))
  (get-after-fval! [_] after-fval)
  (set-after-fval! [_ new-val] (set! after-fval new-val)))

(deftype PartitionBy [f icombinef ireducef coll]
  clojure.core.protocols/CollReduce
  (coll-reduce [this f1]
    (clojure.core.protocols/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [fval (UnsynchronizedRef. nothing)
          part (UnsynchronizedRef. nothing)
          maybe-ireducef #(if (reduced? %) % (ireducef % %2))
          xf (fn [ret val]
               (let [old-fval (get-val! fval)
                     new-fval (f val)]
                 (if (identical? old-fval new-fval)
                   ;; still same partition, append to current part
                   ;; and return unchanged ret
                   (do (set-val! part (maybe-ireducef (get-val! part)
                                                      val))
                       ret)
                   ;; new partition, process finished part,
                   ;; create new one and return new ret
                   (let [finished-part (dered (get-val! part))]
                     (set-val! fval new-fval)
                     ;; use icombinef to initialize partition
                     (set-val! part (ireducef (icombinef) val))
                     ;; first time we just establish the fval,
                     ;; in all other cases, process finished part
                     (if (nothing? old-fval)
                       ret
                       (f1 ret finished-part))))))
          ;; reduce underlying coll with modified reducing fn
          ret (clojure.core.protocols/coll-reduce coll xf init)
          last-part (dered (get-val! part))]
      ;; process last part of the collection
      (if (nothing? last-part) ret (f1 ret last-part))))
  clojure.core.reducers/CollFold
  (coll-fold [this n combinef reducef]
    (let [reduce-one (comp dered reducef)
          reduce-start #(reduce-one (combinef) %)
          ireduce-one (comp dered ireducef)
          ireduce-start #(ireduce-one (icombinef) %)
          combine (fn ([a b] (combinef a b))
                     ([a b c] (combinef a (combinef b c))))
          xcf (fn
                ;; establish initial wrapped return value
                ([]
                   (UnsynchronizedPart. nothing nothing (combinef)
                                        nothing nothing))
                ;; combine wrapped values
                ;; this is a tough fn
                ([l r]
                   (let [lbefore-fval (get-before-fval! l)
                         lret (get-ret! l)
                         lafter (get-after! l)
                         lafter-fval (get-after-fval! l)
                         rbefore (get-before! r)
                         rbefore-fval (get-before-fval! r)
                         rret (get-ret! r)
                         rafter (get-after! r)
                         rafter-fval (get-after-fval! r)
                         ;; chunk is when no split was performed
                         lchunk? (nothing? lbefore-fval)
                         rchunk? (nothing? rbefore-fval)]
                     (cond
                      ;; merge chunks
                      (and lchunk? rchunk?)
                      (if (identical? lafter-fval rafter-fval)
                        (if (nothing? lafter-fval)
                          ;; both are empty chunks
                          (do
                            (set-ret! l (combinef lret rret))
                            l)
                          ;; neither chunk is empty
                          (do
                            (set-ret! l (combinef lret rret))
                            (set-after! l (icombinef lafter rafter))
                            l))
                        (if (nothing? rafter-fval)
                          ;; right chunk is empty
                          (do
                            (set-ret! l (combinef lret rret))
                            l)
                          ;; create segment
                          (do
                            (set-ret! r (combinef lret rret))
                            (set-before! r lafter)
                            (set-before-fval! r lafter-fval)
                            r)))
                      ;; append chunk to right segment
                      lchunk?
                      (if (identical? lafter-fval rbefore-fval)
                        ;; same fval
                        (do
                          (set-ret! r (combinef lret rret))
                          (set-before! r (icombinef lafter rbefore))
                          r)
                        ;; process and set new after
                        (if (nothing? lafter-fval)
                          ;; but lchunk is empty
                          (do
                            (set-ret! r (combinef lret rret))
                            r)
                          ;; all is well
                          (do
                            (set-ret! r (combine
                                         lret
                                         (reduce-start rbefore)
                                         rret))
                            (set-before! r lafter)
                            (set-before-fval! r lafter-fval)
                            r)))
                      ;; append chunk to left segment
                      rchunk?
                      (if (identical? lafter-fval rafter-fval)
                        ;; same fval
                        (do
                          (set-ret! l (combinef lret rret))
                          (set-after! l (icombinef lafter rafter))
                          l)
                        ;; process and set new after
                        (if (nothing? rafter-fval)
                          ;; but rchunk is empty
                          (do
                            (set-ret! l (combinef lret rret))
                            l)
                          ;; all is well
                          (do
                            (set-ret! l (combine lret
                                                 (reduce-start lafter)
                                                 rret))
                            (set-after! l rafter)
                            (set-after-fval! l rafter-fval)
                            l)))
                      ;; merge segments
                      :else
                      (if (identical? lafter-fval rbefore-fval)
                        ;; merge partials and process
                        (do
                          (set-ret! l (combine
                                       lret
                                       (reduce-start
                                        (icombinef lafter rbefore))
                                       rret))
                          (set-after! l rafter)
                          (set-after-fval! l rafter-fval)
                          l)
                        ;; do not merge parts
                        (do
                          (set-ret! l (combine lret
                                               (reduce-one
                                                (reduce-start lafter)
                                                rbefore)
                                               rret))
                          (set-after! l rafter)
                          (set-after-fval! l rafter-fval)
                          l))))))
          xrf (fn [wrapped-ret val]
                (let [after (get-after! wrapped-ret)
                      after-fval (get-after-fval! wrapped-ret)
                      new-fval (f val)]
                  (if (identical? after-fval new-fval)
                    ;; no split, accumulate partition
                    (set-after! wrapped-ret (ireduce-one after val))
                    ;; split and process finished partition
                    (do
                      (set-after-fval! wrapped-ret new-fval)
                      (set-after! wrapped-ret (ireduce-start val))
                      (if (nothing? (get-before-fval! wrapped-ret))
                        ;; before partition is not created yet
                        (do
                          (set-before! wrapped-ret after)
                          (set-before-fval! wrapped-ret after-fval))
                        ;; OK to reduce into the result
                        (let [ret (get-ret! wrapped-ret)]
                          (set-ret! wrapped-ret
                                    (reduce-one ret after))))))
                  wrapped-ret))
          ;; fold underlying coll with modified combine and reduce fns
          wrapped (clojure.core.reducers/coll-fold coll n xcf xrf)
          ;; process before and after leftovers, if present
          ret (get-ret! wrapped)
          ret (if (nothing? (get-before-fval! wrapped))
                ret
                (combinef (reduce-start (get-before! wrapped)) ret))
          ret (if (nothing? (get-after-fval! wrapped))
                ret
                (combinef ret (reduce-start (get-after! wrapped))))]
      ret)))

;;;; Public API

(defn partition-by
  "Applies f to each value in coll, splitting it each time f returns
   a new value. Returns a reducible and foldable collection of
   partitions. Each partition is created by reducing /folding 
   its elements with ireducef and icombinef.
   This version is naive, without any optimizations."
  ([f ireducef coll]
     (PartitionBy. f ireducef ireducef coll))
  ([f icombinef ireducef coll]
     (PartitionBy. f icombinef ireducef coll)))

(defn split
  "Returns reducible and foldable collection of splitted strings
   according to whitespace-fn. Returned collection does not contain
   empty strings. If keep-whitespace? is true (defaults to false),
   returned collection will contain 'whitespace chunks'."
  ([whitespace-fn text-seq]
     (split whitespace-fn false text-seq))
  ([whitespace-fn keep-whitespace? text-seq]
     (let [f (fn [^CharSequence x] (whitespace-fn (.charAt x 0)))
           cf (fn ([] (StringBuilder. 5))
                ([^StringBuilder l ^CharSequence r]
                   (.append l r)
                   ;; (.concat (.toString l) (.toString r))
                   ))
           rf (fn [^StringBuilder l ^Character r] (.append l r))]
       (->> text-seq
            (partition-by whitespace-fn cf rf)
            (r/map (fn [^CharSequence sb] (.toString sb)))
            (->>/when-not keep-whitespace?
              (r/remove f))))))
