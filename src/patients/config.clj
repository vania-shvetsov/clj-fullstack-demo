(ns patients.config
  (:require [aero.core :refer [read-config]]
            [mount.core :refer [defstate]]
            [clojure.java.io :as io]))

(defstate config
  :start
  (let [env (keyword (or (java.lang.System/getenv "ENV") "dev"))]
    (read-config (io/resource "config.edn") {:profile env})))
