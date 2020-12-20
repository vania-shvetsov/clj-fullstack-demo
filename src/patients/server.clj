(ns patients.server
  (:require [mount.core :refer [defstate]]
            [ring.adapter.jetty :as jetty]
            [patients.config :refer [config]]
            [patients.app :refer [app]]))

(defstate server
  :start
  (jetty/run-jetty #'app {:port (-> config :jetty :port)
                          :join? (-> config :jetty :join?)})
  :stop
  (.stop server))
