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

(ns wagjo.util.thread-last
  "Helper functions for ->> threading."
  (:refer-clojure :exclude [when when-not]))

;;;; Implementation details

;;;; Public API

(defmacro when-not
  "If pred is false, threads x through body, otherwise returns
   x unchanged. Inspired by lonocloud/synthread."
  [pred & body]
  (let [x (last body)
        body (butlast body)]
    `(let [x# ~x]
       (if ~pred
         x#
         (->> x# ~@body)))))
