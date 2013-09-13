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
            [clojure.core.protocols :as p]))

;;;; Implementation details

(set! *warn-on-reflection* true)

(def ^:private fjinvoke @#'r/fjinvoke)
(def ^:private fjfork @#'r/fjfork)
(def ^:private fjjoin @#'r/fjjoin)
(def ^:private fjtask @#'r/fjtask)

(declare split)

(deftype IndexOfSplit [^int delim ^String string ^int start ^int end]
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
            (let [ret (f1 ret (.substring string from end))]
              (if (reduced? ret) @ret ret)))
          ;; found delimiter
          (if (== from to)
            ;; no text found between delimiters
            (recur ret (unchecked-inc to))
            ;; text found, process it
            (let [ret (f1 ret (.substring string from to))]
              (if (reduced? ret)
                @ret
                (recur ret (unchecked-inc to)))))))))
  clojure.core.reducers/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (unchecked-subtract-int end start)]
      (cond
       (zero? l) (combinef)
       (<= l n) (p/coll-reduce this reducef (combinef))
       :else (let [i (+ start (quot l 2))
                   ;; shift split point so that we
                   ;; don't have to merge strings
                   i (.indexOf string delim i)]
               (if (== -1 i)
                 (p/coll-reduce this reducef (combinef))
                 (let [v1 (split delim string start i)
                       v2 (split delim string (inc i) end)
                       fc (fn [child]
                            #(r/coll-fold child n combinef reducef))
                       ff #(let [f1 (fc v1)
                                 t2 (fjtask (fc v2))]
                             (fjfork t2)
                             (combinef (f1) (fjjoin t2)))]
                   (fjinvoke ff))))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection containing splitted
   strings based on specified delimiter character. Returned
   collection does not contain any 'whitespace chunks',
   nor empty strings."
  ([delim ^String text]
     (split delim text 0 (.length text)))
  ([delim ^String text start end]
     (IndexOfSplit. (int delim) text start end)))