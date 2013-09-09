;; Copyright (C) 2013, Jozef Wagner. All rights reserved.

(ns wagjo.split.core
  "Describe ways to split a string in Clojure.
   Prepared forms for benchmarking."
  (:require [clojure.string :as str]
            [criterium.core :refer :all]
            [wagjo.split.generator :refer [corpus]]
            [wagjo.split.indexof :as siof]
            [clojure.core.reducers :as r]))

;;;; Implementation details

(defn ^:private guess-chunk-size
  "Given a string s, returns an estimated chunk size for fold
   operation on that string.
   Author: https://github.com/pmbauer/"
  [s]
  (max (/ (count s)
          (* 2 (.availableProcessors (Runtime/getRuntime))))
       4096))

(def ^:private text
  "text which is being splitted"
  "")

(defmacro ^:private foldit
  "Helper macro to run the folding."
  [expr]
  `(r/fold (guess-chunk-size text) r/cat r/append!
           ~expr))

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
  ;; * very slow, should not be used ever
  ;; TODO

  ;; ==== naive iterative reducer/folder
  ;; * slow but straightforward
  ;; TODO

  ;; ==== mutable iterative reducer/folder
  ;; * fastest flexible variant
  ;; TODO

  
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
  ;; TODO

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
  
  ;; benchmark reducer
  (time (count (into [] (siof/split \space text))))
  (with-progress-reporting
    (bench (into [] (siof/split \space text)) :verbose))

  ;; benchmark folder
  (time (count (into [] (foldit (siof/split \space text)))))
  (with-progress-reporting
    (bench (into [] (foldit (siof/split \space text))) :verbose))
  
)
