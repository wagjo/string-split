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

(ns wagjo.split.algo.partitionby-string
  "Generic reducible and foldable partition-by."
  (:refer-clojure :exclude [partition-by remove])
  (:require [clojure.core.reducers :as r]
            [clojure.core.protocols :as p]
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

(deftype SharedString [^chars ca ^long offset ^long count]
  java.io.Serializable
  #_Object
  #_(equals [this other]
    (cond
     (identical? this other) true
     ;; NOTE: not commutative
     (or (instance? SharedString other)
         (instance? CharSequence other))
     (.equals (.toString this) (.toString other))
     :else false))
  Comparable
  (compareTo [this other]
    (.compareTo (.toString this) (.toString other)))
  CharSequence
  (charAt [_ index]
    (when (or (neg? index)
              (>= index count))
      (throw (StringIndexOutOfBoundsException. index)))
    (aget ca (+ offset index)))
  (length [this]
    count)
  (subSequence [this start end]
    (when (neg? start)
      (throw (StringIndexOutOfBoundsException. start)))
    (when (> end count)
      (throw (StringIndexOutOfBoundsException. end)))
    (when (> start end)
      (throw (StringIndexOutOfBoundsException. (- end start))))
    (if (and (zero? start) (== count end))
      this
      (SharedString. ca (+ offset start) (- end start))))
  (toString [this]
    (String. ca offset count)))

(defn fold-split
  "Returns an index where the split is safe, or returns nil."
  [^chars ca ^long start ^long end f]
  (let [fval (f (aget ca start))]
    (loop [x (inc start)]
      (cond
       (== x end) nil
       (identical? fval (f (aget ca x))) (recur (inc x))
       :else x))))

(deftype StringPartition [^chars ca f ^long offset ^long count]
  p/CollReduce
  (coll-reduce [this f1]
    (p/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [end (+ offset count)]
      (if (zero? count)
        init
        (loop [i offset
               ret init
               unknown-after offset
               fval (f (aget ca i))]
          (if (== i end)
            (let [ret (f1 ret
                          (SharedString. ca unknown-after
                                         (- (+ offset count)
                                            unknown-after)))]
              (if (reduced? ret) @ret ret))
            (let [nfval (f (aget ca i))]
              (if (identical? fval nfval)
                (recur (unchecked-inc i) ret unknown-after fval)
                (let [val
                      (SharedString. ca unknown-after
                                     (- i unknown-after))
                      ret (f1 ret val)]
                  (if (reduced? ret)
                    @ret
                    (recur (unchecked-inc i) ret i nfval))))))))))
  r/CollFold
  (coll-fold [this n combinef reducef]
    (let [rf #(p/coll-reduce this reducef (combinef))]
      (cond
       (zero? count) (combinef)
       (<= count n) (rf)
       :else
       (if-let [split (fold-split ca
                                  (+ offset (quot count 2))
                                  (+ offset count)
                                  f)]
         (let [c1 (StringPartition. ca f offset (- split offset))
               c2 (StringPartition. ca f split
                                    (- count (- split offset)))
               fc (fn [child] #(r/coll-fold child n combinef reducef))]
           (fjinvoke #(let [f1 (fc c1)
                            t2 (fjtask (fc c2))]
                        (fjfork t2)
                        (combinef (f1) (fjjoin t2)))))
         (rf))))))

;;;; Public API

(defn partition-by
  "Applies f to each value in pvec, splitting it each time f returns
   a new value. Returns a reducible and foldable collection of
   partitions. This version works only on strings."
  [f ^String string]
  (let [ff (.getDeclaredField String "value")
        _ (.setAccessible ff true)
        ^chars ca (.get ff string)]
    (StringPartition. ca f 0 (.length string))))

(defn split
  "Returns reducible and foldable collection of splitted strings
   according to whitespace-fn. Returned collection does not contain
   empty strings. If keep-whitespace? is true (defaults to false),
   returned collection will contain 'whitespace chunks'.
   If shared? is true (defaults to false), returned collection
   contains custom implementation of CharSequence which shares
   data with analyzed string, instead of containing String objects."
  ([whitespace-fn string]
     (split whitespace-fn false string))
  ([whitespace-fn keep-whitespace? string]
     (split whitespace-fn keep-whitespace? false string))
  ([whitespace-fn keep-whitespace? shared? string]
     (let [f (fn [^CharSequence x] (whitespace-fn (.charAt x 0)))]
       (->> (partition-by whitespace-fn string)
            (->>/when-not shared?
              (r/map (fn [^CharSequence sb] (.toString sb))))
            (->>/when-not keep-whitespace?
              (r/remove f))))))
