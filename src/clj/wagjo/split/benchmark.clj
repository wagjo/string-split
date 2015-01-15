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
            [incanter core stats charts datasets]
            [wagjo.util.generator :refer [corpus]]
            [wagjo.split.algo.indexof :as siof]
            [wagjo.split.algo.lazy :as slazy]
            [wagjo.split.algo.regex :as sregex]
            [wagjo.split.algo.tokenizer :as stoken]
            [wagjo.split.algo.partitionby :as spart]
            [wagjo.split.algo.partitionby-naive :as snaive]
            [wagjo.split.algo.partitionby-shift :as sshift]
            [wagjo.split.algo.partitionby-string :as sstring]))

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
           :doc "Text which is being splitted, as a string"}
  text
  "")

(def ^{:private true
       :doc "Text seq, used in partition-by variants"}
  text-seq
  (doall (seq text)))

(def ^{:private true
       :doc "Text vec, used when folding"}
  text-vec
  (vec text-seq))

(defmacro ^:private parallel
  "Helper macro to run the folding."
  ([fld]
     `(parallel ~fld (guess-chunk-size text))) ;; sorry
  ([fld chunk-size]
     `(r/fold ~chunk-size r/cat r/append! ~fld)))

(defmacro ^:private timed
  "Helper macro to run the simple time with count."
  [red]
  `(time (count ~red)))

(defmacro ^:private timed-into
  "Helper macro to run the simple time with count."
  [& bodyu]
  `(timed (into [] ~@expr)))

(defmacro ^:private benchmarked
  "Helper macro to run the benchmarking."
  [& expr]
  `(with-progress-reporting (bench (do ~@expr) :verbose)))

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

  
  ;;;; PARTITION-BY ON COLLECTION OF CHARACTERS

  
  ;;; lazy-seqs

  (timed (into [] (slazy/split whitespace? text-seq)))
  (timed (into [] (slazy/split whitespace? true text-seq)))

  (benchmarked (into [] (slazy/split whitespace? text-seq)))
  (benchmarked (into [] (slazy/split whitespace? true text-seq)))

  ;;; naive iterative reducer/folder

  (timed (into [] (snaive/split whitespace? text-seq)))
  (timed (into [] (snaive/split whitespace? true text-seq)))
  (timed (into [] (parallel (snaive/split whitespace? text-vec))))
  (timed (into []
               (parallel (snaive/split whitespace? true text-vec))))

  (benchmarked (into [] (snaive/split whitespace? text-seq)))
  (benchmarked (into [] (snaive/split whitespace? true text-seq)))
  (benchmarked
   (into [] (parallel (snaive/split whitespace? text-vec))))
  (benchmarked
   (into [] (parallel (snaive/split whitespace? true text-vec))))

  ;;; mutable iterative reducer/folder

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

  ;;; mutable iterative reducer/folder with shift

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

  
  ;;;; SPLIT ON STRING

  
  ;;; regex reducer/folder

  (timed (into [] (sregex/split #"\S+" text)))
  (timed (into [] (sregex/split #"\S+|\s+" text)))
  (timed (into [] (parallel (sregex/split #"\S+" text))))
  (timed (into [] (parallel (sregex/split #"\S+|\s+" text))))

  (benchmarked (into [] (sregex/split #"\S+" text)))
  (benchmarked (into [] (sregex/split #"\S+|\s+" text)))
  (benchmarked (into [] (parallel (sregex/split #"\S+" text))))
  (benchmarked (into [] (parallel (sregex/split #"\S+|\s+" text))))

  ;;; StringTokenizer reducer/folder

  (=
   #_(into [] (siof/split \space text))
   (into [] (stoken/split " \t\r\n" true text))
   (into [] (parallel (stoken/split " \t\r\n" true text))))
  
  (timed (into [] (stoken/split " \t\r\n" text)))
  (timed (into [] (stoken/split " \t\r\n" true text)))
  (timed (into [] (parallel (stoken/split " \t\r\n" text))))
  (timed (into [] (parallel (stoken/split " \t\r\n" true text))))
  
  (benchmarked (into [] (stoken/split " \t\r\n" text)))
  (benchmarked (into [] (stoken/split " \t\r\n" true text)))
  (benchmarked (into [] (parallel (stoken/split " \t\r\n" text))))
  (benchmarked
   (into [] (parallel (stoken/split " \t\r\n" true text))))

  ;;; optimized iterative reducer/folder

  ;; NOTE: special variant returns CharSequences which shares
  ;;       underlying strings

  (timed (into [] (sstring/split whitespace? text)))
  (timed (into [] (sstring/split whitespace? true text)))
  (timed (into [] (sstring/split whitespace? false true text)))
  (timed (into [] (sstring/split whitespace? true true text)))
  (timed (into [] (parallel (sstring/split whitespace? text))))
  (timed (into [] (parallel (sstring/split whitespace? true text))))
  (timed
   (into [] (parallel (sstring/split whitespace? false true text))))
  (timed
   (into [] (parallel (sstring/split whitespace? true true text))))
  
  (benchmarked (into [] (sstring/split whitespace? text)))
  (benchmarked (into [] (sstring/split whitespace? true text)))
  (benchmarked (into [] (sstring/split whitespace? false true text)))
  (benchmarked (into [] (sstring/split whitespace? true true text)))
  (benchmarked (into [] (parallel (sstring/split whitespace? text))))
  (benchmarked
   (into [] (parallel (sstring/split whitespace? true text))))
  (benchmarked
   (into [] (parallel (sstring/split whitespace? false true text))))
  (benchmarked
   (into [] (parallel (sstring/split whitespace? true true text))))
  
  ;;; indexOf reducer/folder
  
  ;; NOTE: special variant returns CharSequences which shares
  ;;       underlying strings

  (timed (into [] (siof/split \space text)))
  (timed (into [] (siof/split \space true text)))
  (timed (into [] (siof/split \space false true text)))
  (timed (into [] (siof/split \space true true text)))
  (timed (into [] (parallel (siof/split \space text))))
  (timed (into [] (parallel (siof/split \space true text))))
  (timed (into [] (parallel (siof/split \space false true text))))
  (timed (into [] (parallel (siof/split \space true true text))))

  (benchmarked (into [] (siof/split \space text)))
  (benchmarked (into [] (siof/split \space true text)))
  (benchmarked (into [] (siof/split \space false true text)))
  (benchmarked (into [] (siof/split \space true true text)))
  (benchmarked (into [] (parallel (siof/split \space text))))
  (benchmarked (into [] (parallel (siof/split \space true text))))
  (benchmarked
   (into [] (parallel (siof/split \space false true text))))
  (benchmarked
   (into [] (parallel (siof/split \space true true text))))

  ;; chart without whitespace chunks

  (do
    (def ch-label ["lazy" "naive" "naive" "mutable" "mutable" "mutable"])
    
    (def ch-data [3200 1550 2500 575 213 189])
    
    (def ch-groups ["Reduce" "Reduce" "Fold" "Reduce" "Fold" "Shifting fold"])

    (def s-label ["regex" "regex" "tokenizer" "tokenizer" "string partition-by" "string partition-by" "string partition-by shared" "string partition-by shared" "indexOf" "indexOf" "indexOf shared" "indexOf shared"])
    
    (def s-data [296 84 134 71 225 54 161 42 80 37 52 27])
    
    (def s-groups ["Reduce" "Fold" "Reduce" "Fold" "Reduce" "Fold" "Reduce" "Fold" "Reduce" "Fold" "Reduce" "Fold" ])

    (def ch (incanter.charts/bar-chart
             ch-label ch-data
              :group-by ch-groups
              :title "partition-by on a sequence of characters (lower is better)"
              :x-label "variants"
              :y-label "time (ms)"
              :legend true
              :vertical false
              ))

    (def s (incanter.charts/bar-chart
              s-label s-data
              :group-by s-groups
              :title "String split (lower is better)"
              :x-label "variants"
              :y-label "time (ms)"
              :legend true
              :vertical false
              ))

    #_(def chl (incanter.charts/set-axis
              ch :y (incanter.charts/log-axis)))
    
    (incanter.core/view ch)
    (incanter.core/view s))


  (def plot (incanter.charts/bar-chart ["a" "b" "c"] [10 20 30] :legend true :series-label "s1"))
  
  (incanter.core/view plot)
  
  (incanter.charts/add-categories plot ["a" "b" "c"] [5 25 40] :series-label "s2") 
  
)
