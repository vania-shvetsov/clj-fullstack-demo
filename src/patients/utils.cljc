(ns patients.utils
  (:require [clojure.string :as string]))

(defn ->kebab-case-string [s]
  (string/replace s #"_" "-"))

(defn find-index [pred xs]
  (first (keep-indexed #(when (pred %2) %1) xs)))

(defn- class-names-map [map]
  (reduce (fn [map-arr [key value]]
            (if (true? value)
              (conj map-arr key)
              map-arr))
          []
          map))

(defn class-names [& args]
  (clojure.string/join
   " "
   (mapv name
         (reduce (fn [arr arg]
                   (cond
                     (or (string? arg)
                         (symbol? arg)
                         (keyword? arg)) (conj arr arg)
                     (vector? arg) (vec (concat arr arg))
                     (map? arg) (vec (concat arr (class-names-map arg)))
                     :else arr))
                 []
                 args))))
