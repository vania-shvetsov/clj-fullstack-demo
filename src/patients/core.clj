(ns patients.core
  (:require [mount.core :as mount]
            [patients.config]
            [patients.server]
            [patients.db])
  (:gen-class))

;; TODO: add kill signal handling

(defn -main []
  (mount/start))
