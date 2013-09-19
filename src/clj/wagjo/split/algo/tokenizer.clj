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

(deftype TokenizerSplit [^String delim ^String string]
  p/CollReduce
  (coll-reduce [this f1]
    (p/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [m (StringTokenizer. string delim)]
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
         (let [c1 (TokenizerSplit. delim (.substring string 0 split))
               c2 (TokenizerSplit. delim (.substring string split))
               fc (fn [child] #(r/coll-fold child n combinef reducef))]
           (fjinvoke #(let [f1 (fc c1)
                            t2 (fjtask (fc c2))]
                        (fjfork t2)
                        (combinef (f1) (fjjoin t2)))))
         (rf))))))

(defprotocol IUnsynchronizedRef
  (get-val! [_])
  (set-val! [_ new-val]))

(deftype UnsynchronizedRef [^:unsynchronized-mutable val]
  IUnsynchronizedRef
  (get-val! [_] val)
  (set-val! [_ new-val] (set! val new-val)))

(defn whitespace-chunk?
  "Returns true if given string is a delimiter."
  [^String delim ^String string]
  (not (== -1 (.indexOf delim (.codePointAt string 0)))))

(defn keeping-fold-split
  "Returns an index where the keeping split is safe, or returns nil."
  [^String string start delim]
  (let [string (.substring string start)
        m (StringTokenizer. string delim true)]
    (loop [offset start]
      (when (.hasMoreTokens m)
        (let [s (.nextToken m)]
          (if (whitespace-chunk? delim s)
            (recur (inc offset))
            (+ offset (.length s))))))))

(deftype KeepingTokenizerSplit [^String delim ^String string]
  p/CollReduce
  (coll-reduce [this f1]
    (p/coll-reduce this f1 (f1)))
  (coll-reduce [_ f1 init]
    (let [m (StringTokenizer. string delim true)
          wch (UnsynchronizedRef. nil)
          ret (loop [ret init]
                (if-not (.hasMoreTokens m)
                  ret
                  (let [s (.nextToken m)]
                    (if (whitespace-chunk? delim s)
                      ;; accumulate whitespace chunk
                      ;; and return unchanged ret
                      (do
                        (if-let [pwch (get-val! wch)]
                          (set-val! wch (str pwch s))
                          (set-val! wch s))
                        (recur ret))
                      ;; process leftover whitespace chunk
                      ;; and then process current token
                      (let [ret (if-let [pwch (get-val! wch)]
                                  (do
                                    (set-val! wch nil)
                                    (f1 ret pwch))
                                  ret)]
                        (if (reduced? ret)
                          @ret
                          (let [ret (f1 ret s)]
                            (if (reduced? ret)
                              @ret
                              (recur ret)))))))))]
      ;; process leftover whitespace chunk
      (if-let [pwch (get-val! wch)]
        (let [ret (f1 ret pwch)]
          (if (reduced? ret) @ret ret))
        ret)))
  r/CollFold
  (coll-fold [this n combinef reducef]
    (let [l (.length string)
          rf #(p/coll-reduce this reducef (combinef))]
      (cond
       (.isEmpty string) (combinef)
       (<= l n) (rf)
       :else
       (if-let [split (keeping-fold-split string (quot l 2) delim)]
         (let [c1 (KeepingTokenizerSplit. delim
                                          (.substring string 0 split))
               c2 (KeepingTokenizerSplit. delim
                                          (.substring string split))
               fc (fn [child] #(r/coll-fold child n combinef reducef))]
           (fjinvoke #(let [f1 (fc c1)
                            t2 (fjtask (fc c2))]
                        (fjfork t2)
                        (combinef (f1) (fjjoin t2)))))
         (rf))))))

;;;; Public API

(defn split
  "Returns reducible and foldable collection of splitted strings
   delimited by one of given delimiters. Returned collection does not
   contain empty strings. If keep-whitespace? is true (defaults to
   false), returned collection will contain 'whitespace chunks'."
  ([delimiters string]
     (split delimiters string false))
  ([delimiters string keep-whitespace?]
     (if keep-whitespace?
       (KeepingTokenizerSplit. delimiters string)
       (TokenizerSplit. delimiters string))))
