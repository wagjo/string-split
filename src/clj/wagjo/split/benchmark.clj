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

(ns wagjo.split.benchmark
  "Describe ways to split a string in Clojure.
   Prepared forms for benchmarking."
  (:require [clojure.core.reducers :as r]
            [clojure.string :as str]
            [criterium.core :refer :all]
            [wagjo.util.generator :refer [corpus]]
            [wagjo.split.algo.indexof :as siof]
            [wagjo.split.algo.lazy :as slazy]
            [wagjo.split.algo.regex :as sregex]
            [wagjo.split.algo.partitionby :as spart]
            [wagjo.split.algo.partitionby-naive :as snaive]
            [wagjo.split.algo.partitionby-shift :as sshift]))

;;;; Implementation details

(defn ^:private available-processors
  "Returns number of available processors on this machine."
  []
  (.availableProcessors (Runtime/getRuntime)))

(defn ^:private guess-chunk-size
  "Given a string s, returns an estimated chunk size for fold
   operation on that string."
  [s]
  (-> (count s)
      (/ 2)
      (/ (available-processors))
      (max 4096)))

(defonce ^{:private true
           :doc "text which is being splitted"}
  text
  "")

(def ^{:private true
       :doc "text seq which is being splitted"}
  text-seq
  (doall (seq text)))

(def ^{:private true
       :doc "text seq which is being splitted"}
  text-vec
  (vec text-seq))

(defmacro ^:private parallel
  "Helper macro to run the folding."
  ([expr]
     `(parallel ~expr (guess-chunk-size text)))
  ([expr chunk-size]
     `(r/fold ~chunk-size r/cat r/append! ~expr)))

(defmacro ^:private timed
  "Helper macro to run the simple time with count."
  [& expr]
  `(time (count ~@expr)))

(defmacro ^:private benchmarked
  "Helper macro to run the benchmarking."
  [& expr]
  `(with-progress-reporting (bench (do ~@expr) :verbose)))

(defn ^:private whitespace?
  "Returns true if given character is considered a whitespace."
  [^Character c]
  (or (.equals c (char 32))
      (.equals c (char 9))
      (.equals c (char 10))
      (.equals c (char 13))))

;;;; Testing

(comment

  ;; corpus
  (time (def text (corpus 10)))
  (time (def text (corpus 1000)))
  (time (def text (corpus 1000000)))

  ;; == Various ways to split a string
  ;; * everything implemented as both reducible
  ;;   and foldable collection
  ;; ** except variants using lazy seqs
  ;; ** reducer variants support early termination e.g. with r/take
  ;; * most variants have limits on what is a delimiter, or
  ;;   how the result will look like
  ;; ** in return they deliver better performance
  ;; * two categories
  ;; ** partition-by on a collection of characters
  ;; ** splitting a string

  ;; === partition-by on collection of characters
  ;; * supports infinite collections
  ;; * does not need whole string in memory at once
  ;; * supports any predicate fn
  ;; * small memory usage

  ;; ==== lazy-seqs
  ;; * very slow, should not be used, ever
  ;; * no parallel variant
  ;; * can keep whitespace chunks in the result

  (timed (into [] (slazy/split whitespace? text-seq)))
  (timed (into [] (slazy/split whitespace? true text-seq)))
  (benchmarked (into [] (slazy/split whitespace? text-seq)))
  (benchmarked (into [] (slazy/split whitespace? true text-seq)))

  ;; ==== naive iterative reducer/folder
  ;; * slow but straightforward

  (timed (into [] (snaive/split whitespace? text-seq)))
  (timed (into [] (snaive/split whitespace? true text-seq)))
  (timed (into [] (parallel (snaive/split whitespace? text-vec))))
  (timed (into []
               (parallel (snaive/split whitespace? true text-vec))))

  (benchmarked (into [] (snaive/split whitespace? text-seq)))
  (benchmarked (into [] (snaive/split whitespace? true text-seq)))

  (benchmarked (into [] (snaive/split whitespace? text-vec)))
  (benchmarked (into [] (snaive/split whitespace? true text-vec)))
  (benchmarked
   (into [] (parallel (snaive/split whitespace? text-vec))))
  (benchmarked
   (into [] (parallel (snaive/split whitespace? true text-vec))))

  ;; ==== mutable iterative reducer/folder
  ;; * fastest flexible variant

  (timed (into [] (spart/split whitespace? text-seq)))
  (timed (into [] (spart/split whitespace? true text-seq)))
  (timed (into [] (parallel (spart/split whitespace? text-vec))))
  (timed (into []
               (parallel (spart/split whitespace? true text-vec))))
  (benchmarked (into [] (spart/split whitespace? text-seq)))
  (benchmarked (into [] (spart/split whitespace? true text-seq)))
  (benchmarked
   (into [] (parallel (spart/split whitespace? text-vec))))
  (benchmarked
   (into [] (parallel (spart/split whitespace? true text-vec))))

  ;; ==== mutable iterative reducer/folder with shift
  ;; * fastest flexible variant

  (timed (into [] (sshift/split whitespace? text-seq)))
  (timed (into [] (sshift/split whitespace? true text-seq)))
  (timed (into [] (parallel (sshift/split whitespace? text-vec))))
  (timed (into []
               (parallel (sshift/split whitespace? true text-vec))))
  (benchmarked (into [] (sshift/split whitespace? text-seq)))
  (benchmarked (into [] (sshift/split whitespace? true text-seq)))
  (benchmarked
   (into [] (parallel (sshift/split whitespace? text-vec))))
  (benchmarked
   (into [] (parallel (sshift/split whitespace? true text-vec))))

  ;; === split on string
  ;; * very fast because we have all data in memory
  ;; * larger memory usage
  ;; * often strips delimiters from the result
  ;; ** some variants return empty strings for subsequent delimiters
  ;; ** depends on use case whether this is good or not
  ;; * less flexible, each approach has its own limits on delimiters
  ;;   or at the returned result

  ;; ==== regex reducer/folder
  ;; * any regex, may return empty strings

  (=
    (into [] (sshift/split whitespace? text))
    (into [] (sregex/split #"\S+" text))
    (into [] (parallel (sregex/split #"\S+" text))))
  
  (timed (into [] (sregex/split #"\S+" text)))
  (timed (into [] (parallel (sregex/split #"\S+" text))))
  
  (benchmarked (into [] (sregex/split #"\S+" text)))
  (benchmarked (into [] (parallel (sregex/split #"\S+" text))))

  ;; ==== StringTokenizer reducer/folder
  ;; * set of delimiting chars
  ;; TODO

  ;; ==== optimized iterative reducer/folder
  ;; * like flexible partition-by, but optimized for strings
  ;; TODO
  
  ;; ==== indexOf reducer/folder
  ;; * THE fastest reducer/folder, but has specific limitations
  ;; ** delimiter is one character, or string (not implemented yet)
  ;; ** does not keep delimiters (whitespace chunks) in the result
  
  (timed (into [] (siof/split \space text)))
  (timed (into [] (parallel (siof/split \space text))))
  (benchmarked (into [] (siof/split \space text)))
  (benchmarked (into [] (parallel (siof/split \space text))))
  
)
