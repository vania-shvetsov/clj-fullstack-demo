(ns patients.utils
  (:require [clojure.string :as string]))

(defn ->snake-case-string [x]
  (println x)
  (let [x' (if (keyword? x) (name x) x)]
    (string/replace x' #"-" "_")))
