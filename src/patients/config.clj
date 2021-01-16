(ns patients.config
  (:require [aero.core :refer [read-config]]
            [clojure.java.io :as io]))

(defn load-config []
  (let [env (keyword (or (java.lang.System/getenv "ENV") "dev"))]
    (read-config (io/resource "config.edn") {:profile env})))
