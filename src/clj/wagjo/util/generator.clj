;; Copyright (C) 2013, Paul Michael Bauer. All rights reserved.
;; Copyright (C) 2013, Jozef Wagner. All rights reserved.
;; Taken from https://github.com/pmbauer/blogcode.text
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

(ns wagjo.util.generator
  "Text corpus generation."
  (:require [clojure.data.generators :as gen]))

;;;; Implementation details

(def ^:private ascii-alphanumeric
  (vec (map char (concat (range 48 58)       ;; numbers
                         (range 65 91)       ;; uppercase
                         (range 97 123)))))  ;; lowercase

(defn ^:private default-word-sizer
  []
  (gen/uniform 1 17))

(defn ^:private word
  ([]
     (word default-word-sizer))
  ([sizer]
     (gen/string #(gen/rand-nth ascii-alphanumeric) sizer)))

(defn ^:private space
  ([]
     (space (gen/uniform 1 3)))
  ([sizer]
     (gen/string (constantly \space) sizer)))

;;;; Public API

(defn corpus
  "Generates a random string with a given number of words
  sepparated by variable whitespace. Appends trailing whitespace."
  ([word-count] (corpus word-count default-word-sizer))
  ([word-count word-sizer]
     (let [text (StringBuilder.)]
       (dotimes [_ word-count]
         (.append text (word word-sizer))
         (.append text (space)))
       (.toString text))))
