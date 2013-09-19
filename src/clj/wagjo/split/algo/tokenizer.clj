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

(ns wagjo.split.algo.tokenizer
  "Reducible and foldable string splitter based on StringTokenizer."
  (:require [clojure.core.reducers :as r]
            [clojure.core.protocols :as p])
  (:import [java.util StringTokenizer]))

;;;; Implementation details

(set! *warn-on-reflection* true)

(def ^:private fjinvoke @#'r/fjinvoke)
(def ^:private fjfork @#'r/fjfork)
(def ^:private fjjoin @#'r/fjjoin)
(def ^:private fjtask @#'r/fjtask)

(defn fold-split
  "Returns an index where the split is safe, or returns nil."
  [^String string start delim]
  (let [string (.substring string start)
        m (StringTokenizer. string delim true)]
    (when (.hasMoreTokens m)
      (+ start (.length (.nextToken m))))))

(deftype TokenizerSplit [^String delim ^String string ^boolean keep?]
  p/CollReduce
  (coll-reduce [this f1]
    (p/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [m (StringTokenizer. string delim keep?)]
      (loop [ret init]
        (if-not (.hasMoreTokens m)
          ret
          (let [ret (f1 ret (.nextToken m))]
            (if (reduced? ret)
              @ret
              (recur ret)))))))
  r/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (.length string)
          rf #(p/coll-reduce this reducef (combinef))]
      (cond
       (.isEmpty string) (combinef)
       (<= l n) (rf)
       :else
       (if-let [split (fold-split string (quot l 2) delim)]
         (let [c1 (TokenizerSplit. delim
                                   (.substring string 0 split)
                                   keep?)
               c2 (TokenizerSplit. delim
                                   (.substring string split)
                                   keep?)
               fc (fn [child] #(r/coll-fold child n combinef reducef))]
           (fjinvoke #(let [f1 (fc c1)
                            t2 (fjtask (fc c2))]
                        (fjfork t2)
                        (combinef (f1) (fjjoin t2)))))
         (rf))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection of splitted strings
   delimited by one of given delimiters.
   If keep-delimiters? is true, returned collection will contain
   also individual delimiters."
  ([delimiters string]
     (split delimiters string false))
  ([delimiters string keep-delimiters?]
     (TokenizerSplit. delimiters string keep-delimiters?)))
