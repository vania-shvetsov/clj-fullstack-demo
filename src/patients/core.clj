(ns patients.core
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [patients.config]
            [patients.server]
            [patients.db])
  (:gen-class))

(defn- shutdown-hook [cleanup-fn]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable cleanup-fn)))

(defn -main []
  (shutdown-hook (fn []
                   (log/info "Gracefully shutdown, please wait...")
                   (mount/stop)))
  (mount/start))
