(ns patients.utils
  (:require [clojure.string :as string]))

(defn ->kebab-case-string [s]
  (string/replace s #"_" "-"))

(defn find-index [pred xs]
  (first (keep-indexed #(when (pred %2) %1) xs)))
