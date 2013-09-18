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
  (:refer-clojure :exclude [partition-by remove])
  (:require [clojure.core.reducers :as r]
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

(deftype RegexMatch [^java.util.regex.Pattern regexp ^String string]
  clojure.core.protocols/CollReduce
  (coll-reduce [this f1]
    (clojure.core.protocols/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [^java.util.regex.Matcher m (re-matcher regexp string)]
      (loop [ret init]
        (if-not (.find m)
          ret
          (let [ret (f1 ret (.group m))]
            (if (reduced? ret)
              @ret
              (recur ret)))))))
  clojure.core.reducers/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (.length string)]
      (cond
       (.isEmpty string) (combinef)
       (<= l n) (clojure.core.protocols/coll-reduce
                 this reducef (combinef))
       :else
       (let [split (quot l 2)
             ^java.util.regex.Matcher m (re-matcher regexp string)]
         (if-not (.find m split)
           (clojure.core.protocols/coll-reduce
            this reducef (combinef))
           (let [split (.end m)]
             (if (== split l)
               (clojure.core.protocols/coll-reduce
                this reducef (combinef))
               (let [c1 (RegexMatch. regexp
                                     (.substring string 0 split))
                     c2 (RegexMatch. regexp (.substring string split))
                     fc (fn [child] #(clojure.core.reducers/coll-fold
                                     child n combinef reducef))]
                 (fjinvoke
                  #(let [f1 (fc c1)
                         t2 (fjtask (fc c2))]
                     (fjfork t2)
                     (combinef (f1) (fjjoin t2)))))))))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection of splitted strings
   according to regex."
  [regexp string]
  (RegexMatch. regexp string))
