(ns patients.config
  (:require [aero.core :refer [read-config]]
            [mount.core :refer [defstate]]))

(defstate config
  :start
  (let [env (keyword (or (java.lang.System/getenv "ENV") "dev"))]
    (read-config (clojure.java.io/resource "config.edn") {:profile env})))
