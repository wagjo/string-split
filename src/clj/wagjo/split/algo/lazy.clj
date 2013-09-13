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

(ns wagjo.split.algo.lazy
  "String splitting with lazy sequences."
  (:require [wagjo.util.thread-last :as ->>]))

;;;; Public API

(defn split
  "Returns lazy sequence of splitted strings according to
   whitespace-fn. Returned sequence does not contain empty strings.
   If keep-whitespace? is true (defaults to false), returned
   sequence will contain 'whitespace chunks'."
  ([whitespace-fn text-seq]
     (split whitespace-fn false text-seq))
  ([whitespace-fn keep-whitespace? text-seq]
     (->> text-seq
          (partition-by whitespace-fn)
          (->>/when-not keep-whitespace?
            (remove #(whitespace-fn (first %))))
          (map #(apply str %)))))
