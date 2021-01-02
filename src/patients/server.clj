(ns patients.server
  (:require [mount.core :refer [defstate]]
            [ring.adapter.jetty :as jetty]
            [clojure.tools.logging :as log]
            [patients.config :refer [config]]
            [patients.app :refer [app]]))

(defstate server
  :start
  (let [port (-> config :jetty :port)
        join? (-> config :jetty :join?)]
    (jetty/run-jetty #'app {:port port
                            :join? join?}))
  :stop
  (do
    (log/info "Stop server")
    (.stop server)))
