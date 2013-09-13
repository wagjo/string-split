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

(ns wagjo.split.algo.partitionby-naive
  "Generic reducible and foldable partition-by.
   Naive variant without any optimizations."
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

(deftype PartitionBy [f icombinef ireducef coll]
  clojure.core.protocols/CollReduce
  (coll-reduce [this f1]
    (clojure.core.protocols/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [fval (atom nothing)
          part (atom nothing)
          maybe-ireducef #(if (reduced? %) % (ireducef % %2))
          xf (fn [ret val]
               (let [old-fval @fval
                     new-fval (f val)]
                 (if (identical? old-fval new-fval)
                   ;; still same partition, append to current part
                   ;; and return unchanged ret
                   (do (swap! part maybe-ireducef val)
                       ret)
                   ;; new partition, process finished part,
                   ;; create new one and return new ret
                   (let [finished-part (dered @part)]
                     (reset! fval new-fval)
                     ;; use icombinef to initialize partition
                     (reset! part (ireducef (icombinef) val))
                     ;; first time we just establish the fval,
                     ;; in all other cases, process finished part
                     (if (nothing? old-fval)
                       ret
                       (f1 ret finished-part))))))
          ;; reduce underlying coll with modified reducing fn
          ret (clojure.core.protocols/coll-reduce coll xf init)
          last-part (dered @part)]
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
                ([] {:before nothing
                     :before-fval nothing
                     :ret (combinef)
                     :after nothing
                     :after-fval nothing})
                ;; combine wrapped values
                ;; this is a tough fn
                ([l r]
                   (let [{:keys [lbefore-fval lret
                                 lafter lafter-fval]} l
                         {:keys [rbefore rbefore-fval
                                 rret rafter rafter-fval]} r
                         ;; chunk is when no split was performed
                         lchunk? (nothing? lbefore-fval)
                         rchunk? (nothing? rbefore-fval)]
                     (cond
                      ;; merge chunks
                      (and lchunk? rchunk?)
                      (if (identical? lafter-fval rafter-fval)
                        (if (nothing? lafter-fval)
                          ;; both are empty chunks
                          (assoc l :ret (combinef lret rret))
                          ;; neither chunk is empty
                          (assoc l :ret (combinef lret rret)
                                   :after (icombinef lafter rafter)))
                        (if (nothing? rafter-fval)
                          ;; right chunk is empty
                          (assoc l :ret (combinef lret rret))
                          ;; create segment
                          (assoc r :ret (combinef lret rret)
                                   :before lafter
                                   :before-fval lafter-fval)))
                      ;; append chunk to right segment
                      lchunk?
                      (if (identical? lafter-fval rbefore-fval)
                        ;; same fval
                        (assoc r :ret (combinef lret rret)
                                 :before (icombinef lafter rbefore))
                        ;; process and set new after
                        (if (nothing? lafter-fval)
                          ;; but lchunk is empty
                          (assoc r :ret (combinef lret rret))
                          ;; all is well
                          (assoc r :ret (combine
                                         lret
                                         (reduce-start rbefore)
                                         rret)
                                   :before lafter
                                   :before-fval lafter-fval)))
                      ;; append chunk to left segment
                      rchunk?
                      (if (identical? lafter-fval rafter-fval)
                        ;; same fval
                        (assoc l :ret (combinef lret rret)
                                 :after (icombinef lafter rafter))
                        ;; process and set new after
                        (if (nothing? rafter-fval)
                          ;; but rchunk is empty
                          (assoc l :ret (combinef lret rret))
                          ;; all is well
                          (assoc l :ret (combine lret
                                                 (reduce-start lafter)
                                                 rret)
                                   :after rafter
                                   :after-fval rafter-fval)))
                      ;; merge segments
                      :else
                      (if (identical? lafter-fval rbefore-fval)
                        ;; merge partials and process
                        (assoc l :ret (combine
                                       lret
                                       (reduce-start
                                        (icombinef lafter rbefore))
                                       rret)
                                 :after rafter
                                 :after-fval rafter-fval)
                        ;; do not merge parts
                        (assoc l :ret (combine lret
                                               (reduce-one
                                                (reduce-start lafter)
                                                rbefore)
                                               rret)
                                 :after rafter
                                 :after-fval rafter-fval))))))
          xrf (fn [wrapped-ret val]
                (let [{:keys [before-fval ret after after-fval]}
                      wrapped-ret
                      new-fval (f val)]
                  (if (identical? after-fval new-fval)
                    ;; no split, accumulate partition
                    (assoc wrapped-ret :after (ireduce-one after val))
                    ;; split and process finished partition
                    (let [new-wrapped (assoc wrapped-ret
                                        :after-fval new-fval
                                        :after (ireduce-start val))]
                      (cond
                       ;; first time initialization
                       (nothing? after-fval) new-wrapped
                       ;; before partition is not created yet
                       (nothing? before-fval)
                       (assoc new-wrapped :before after
                                          :before-fval after-fval)
                       ;; OK to reduce into the result
                       :else (assoc new-wrapped
                               :ret (reduce-one ret after)))))))
          ;; fold underlying coll with modified combine and reduce fns
          wrapped (clojure.core.reducers/coll-fold coll n xcf xrf)
          ;; process before and after leftovers, if present
          ret (:ret wrapped)
          ret (if (nothing? (:before-fval wrapped))
                ret
                (combinef (reduce-start (:before wrapped)) ret))
          ret (if (nothing? (:after-fval wrapped))
                ret
                (combinef ret (reduce-start (:after wrapped))))]
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
     (let [f (fn [^String x] (whitespace-fn (.charAt x 0)))]
       (->> text-seq
            (partition-by whitespace-fn str)
            (->>/when-not keep-whitespace?
              (r/remove f))))))
