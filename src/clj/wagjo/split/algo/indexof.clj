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

(ns wagjo.split.algo.indexof
  "String splitting with indexof."
  (:require [clojure.core.reducers :as r]
            [clojure.core.protocols :as p]
            [wagjo.util.thread-last :as ->>]))

;;;; Implementation details

(set! *warn-on-reflection* true)

(def ^:private fjinvoke @#'r/fjinvoke)
(def ^:private fjfork @#'r/fjfork)
(def ^:private fjjoin @#'r/fjjoin)
(def ^:private fjtask @#'r/fjtask)

(defn fold-split
  "Returns an index where the split is safe, or returns nil."
  [^String string delim start end]
  (let [^int delim delim]
    (loop [^int i start]
      (let [next (.indexOf string delim i)]
        (cond
         (== next end) nil
         (== next i) (recur (inc i))
         :else next)))))

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

(deftype IndexOfSplit [^int delim ^String string ^chars ca
                       ^int start ^int end]
  clojure.core.protocols/CollReduce
  (coll-reduce [this f1]
    (clojure.core.protocols/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (loop [ret init
           from start]
      (let [to (.indexOf string delim from)]
        (if (or (== -1 to) (<= end to))
          ;; no more delimiters found
          (if (== from end)
            ;; do not process trailing delimiter
            ret
            ;; process trailing text
            (let [ret (f1 ret (SharedString. ca from (- end from)))]
              (if (reduced? ret) @ret ret)))
          ;; found delimiter
          (if (== from to)
            ;; no text found between delimiters
            (recur ret (unchecked-inc from))
            ;; text found, process it
            (let [ret (f1 ret (SharedString. ca from (- to from)))]
              (if (reduced? ret)
                @ret
                (recur ret (unchecked-inc to)))))))))
  clojure.core.reducers/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (unchecked-subtract-int end start)
          rf #(p/coll-reduce this reducef (combinef))]
      (cond
       (zero? l) (combinef)
       (<= l n) (rf)
       :else (if-let [i (fold-split string delim
                                    (+ start (quot l 2))
                                    end)]
               (let [v1 (IndexOfSplit. delim string ca start i)
                     v2 (IndexOfSplit. delim string ca i end)
                     fc (fn [child]
                          #(r/coll-fold child n combinef reducef))
                     ff #(let [f1 (fc v1)
                               t2 (fjtask (fc v2))]
                           (fjfork t2)
                           (combinef (f1) (fjjoin t2)))]
                 (fjinvoke ff))
               (rf))))))

(deftype KeepingIndexOfSplit [^int delim ^String string ^chars ca
                              ^int start ^int end]
  clojure.core.protocols/CollReduce
  (coll-reduce [this f1]
    (clojure.core.protocols/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (loop [ret init
           from start
           from-delim start]
      (let [to (.indexOf string delim from)]
        (if (or (== -1 to) (<= end to))
          ;; no more delimiters found
          (if (== from end)
            ;; do not process trailing delimiter
            (if-not (== from-delim from)
              (let [ret (f1 ret (SharedString. ca from-delim
                                               (- from from-delim)))]
                (if (reduced? ret) @ret ret))
              ret)
            ;; process trailing text
            (if-not (== from-delim from)
              (let [ret (f1 ret (SharedString. ca from-delim
                                               (- from from-delim)))]
                (if (reduced? ret)
                  @ret
                  (let [ret (f1 ret (SharedString. ca from
                                                   (- end from)))]
                    (if (reduced? ret) @ret ret))))
              (let [ret (f1 ret (SharedString. ca from
                                               (- end from)))]
                (if (reduced? ret) @ret ret))))
          ;; found delimiter
          (if (== from to)
            ;; no text found between delimiters
            (recur ret (unchecked-inc from) from-delim)
            ;; text found, process it
            (if-not (== from-delim from)
              (let [ret (f1 ret (SharedString. ca from-delim
                                               (- from from-delim)))]
                (if (reduced? ret)
                  @ret
                  (let [ret (f1 ret (SharedString. ca from
                                                   (- to from)))]
                    (if (reduced? ret)
                      @ret
                      (recur ret (unchecked-inc to) to)))))
              (let [ret (f1 ret (SharedString. ca from (- to from)))]
                (if (reduced? ret)
                  @ret
                  (recur ret (unchecked-inc to) to)))))))))
  clojure.core.reducers/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (unchecked-subtract-int end start)
          rf #(p/coll-reduce this reducef (combinef))]
      (cond
       (zero? l) (combinef)
       (<= l n) (rf)
       :else (if-let [i (fold-split string delim
                                    (+ start (quot l 2))
                                    end)]
               (let [v1 (KeepingIndexOfSplit. delim string ca start i)
                     v2 (KeepingIndexOfSplit. delim string ca i end)
                     fc (fn [child]
                          #(r/coll-fold child n combinef reducef))
                     ff #(let [f1 (fc v1)
                               t2 (fjtask (fc v2))]
                           (fjfork t2)
                           (combinef (f1) (fjjoin t2)))]
                 (fjinvoke ff))
               (rf))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection containing splitted
   strings based on specified delimiter character. Returned
   collection does not contain any 'whitespace chunks',
   nor empty strings."
  ([delim ^String text]
     (split delim false false text 0 (.length text)))
  ([delim keep-whitespace? ^String text]
     (split delim keep-whitespace? false text 0 (.length text)))
  ([delim keep-whitespace? shared? ^String text]
     (split delim keep-whitespace? shared? text 0 (.length text)))
  ([delim keep-whitespace? shared? ^String text start end]
     (let [ff (.getDeclaredField String "value")
           _ (.setAccessible ff true)
           ^chars ca (.get ff text)
           x (if keep-whitespace?
               (KeepingIndexOfSplit. (int delim) text ca start end)
               (IndexOfSplit. (int delim) text ca start end))]
       (->> x
            (->>/when-not shared?
              (r/map (fn [^CharSequence sb] (.toString sb))))))))
