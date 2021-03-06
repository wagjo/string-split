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

(ns wagjo.split.algo.regex
  "Regex string reducible and foldable splitter."
  (:require [clojure.core.reducers :as r]
            [clojure.core.protocols :as p]))

;;;; Implementation details

(set! *warn-on-reflection* true)

(def ^:private fjinvoke @#'r/fjinvoke)
(def ^:private fjfork @#'r/fjfork)
(def ^:private fjjoin @#'r/fjjoin)
(def ^:private fjtask @#'r/fjtask)

(defn fold-split
  "Returns an index where the split is safe, or returns nil."
  [^String string start regex]
  (let [^java.util.regex.Matcher m (re-matcher regex string)]
    (when (.find m start)
      (let [split (.end m)]
        (when-not (== split (.length string))
          split)))))

(deftype RegexMatch [^java.util.regex.Pattern regex ^String string]
  p/CollReduce
  (coll-reduce [this f1]
    (p/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [^java.util.regex.Matcher m (re-matcher regex string)]
      (loop [ret init]
        (if-not (.find m)
          ret
          (let [ret (f1 ret (.group m))]
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
       (if-let [split (fold-split string (quot l 2) regex)]
         (let [c1 (RegexMatch. regex (.substring string 0 split))
               c2 (RegexMatch. regex (.substring string split))
               fc (fn [child] #(r/coll-fold child n combinef reducef))]
           (fjinvoke #(let [f1 (fc c1)
                            t2 (fjtask (fc c2))]
                        (fjfork t2)
                        (combinef (f1) (fjjoin t2)))))
         (rf))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection of splitted strings
   according to given regex."
  [regex string]
  (RegexMatch. regex string))
