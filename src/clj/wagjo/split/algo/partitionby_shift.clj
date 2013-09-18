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

(ns wagjo.split.algo.partitionby-shift
  "Generic reducible and foldable partition-by."
  (:refer-clojure :exclude [partition-by remove])
  (:require [clojure.core.reducers :as r]
            [wagjo.util.thread-last :as ->>]))

;;;; Implementation details

(set! *warn-on-reflection* true)

(def ^:private fjinvoke @#'r/fjinvoke)
(def ^:private fjfork @#'r/fjfork)
(def ^:private fjjoin @#'r/fjjoin)
(def ^:private fjtask @#'r/fjtask)

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
    (let [l (count coll)]
      (cond
       (empty? coll) (combinef)
       (<= l n) (clojure.core.protocols/coll-reduce
                 this reducef (combinef))
       :else
       (loop [split (inc (quot l 2))
              fval (f (nth coll (dec split)))]
         (cond
          ;; at the end of coll, just reduce it all
          (== split l) (clojure.core.protocols/coll-reduce
                        this reducef (combinef))
          ;; found a good place to split
          (not (identical? fval (f (nth coll split))))
          (let [c1 (PartitionBy. f icombinef ireducef
                                 (subvec coll 0 split))
                c2 (PartitionBy. f icombinef ireducef
                                 (subvec coll split l))
                fc (fn [child] #(clojure.core.reducers/coll-fold
                                child n combinef reducef))]
            (fjinvoke
             #(let [f1 (fc c1)
                    t2 (fjtask (fc c2))]
                (fjfork t2)
                (combinef (f1) (fjjoin t2)))))
          ;; try next index
          :else (recur (inc split) (f (nth coll split)))))))))

;;;; Public API

(defn partition-by
  "Applies f to each value in pvec, splitting it each time f returns
   a new value. Returns a reducible and foldable collection of
   partitions. Each partition is created by reducing /folding 
   its elements with ireducef and icombinef.
   This version is optimized and takes persistent vector as input."
  ([f ireducef pvec]
     (PartitionBy. f ireducef ireducef pvec))
  ([f icombinef ireducef pvec]
     (PartitionBy. f icombinef ireducef pvec)))

(defn split
  "Returns reducible and foldable collection of splitted strings
   according to whitespace-fn. Returned collection does not contain
   empty strings. If keep-whitespace? is true (defaults to false),
   returned collection will contain 'whitespace chunks'."
  ([whitespace-fn text-vec]
     (split whitespace-fn false text-vec))
  ([whitespace-fn keep-whitespace? text-vec]
     (let [f (fn [^CharSequence x] (whitespace-fn (.charAt x 0)))
           cf (fn ([] (StringBuilder. 5))
                ([^StringBuilder l ^CharSequence r]
                   (.append l r)
                   ;; (.concat (.toString l) (.toString r))
                   ))
           rf (fn [^StringBuilder l ^Character r] (.append l r))]
       (->> text-vec
            (partition-by whitespace-fn cf rf)
            (r/map (fn [^CharSequence sb] (.toString sb)))
            (->>/when-not keep-whitespace?
              (r/remove f))))))
