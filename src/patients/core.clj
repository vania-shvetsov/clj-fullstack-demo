(ns patients.core
  (:require [aero.core :refer [read-config]]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [patients.config :refer [load-config]]
            [patients.app :as app])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource))
  (:gen-class))

(defn- shutdown-hook [cleanup-fn]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable cleanup-fn)))

(defn create-config []
  (let [env (keyword (or (java.lang.System/getenv "ENV") "dev"))]
    (read-config (io/resource "config.edn") {:profile env})))

(defn- jdbc-url [c]
  ;; "jdbc:postgresql://localhost:5432/testdb"
  (format "jdbc:%s://%s:%s/%s"
          (:subprotocol c)
          (:host c)
          (:port c)
          (:dbname c)))

(defn- create-datasource [c]
  (let [classname (-> c :db :classname)
        url (jdbc-url (:db c))
        user (-> c :db :user)
        password (-> c :db :password)
        max-pool-size (-> c :db-pool :max-pool-size)
        min-pool-size (-> c :db-pool :min-pool-size)]
    (doto (ComboPooledDataSource.)
      (.setDriverClass classname)
      (.setJdbcUrl url)
      (.setUser user)
      (.setPassword password)
      (.setMaxIdleTimeExcessConnections (* 30 60))
      (.setMaxIdleTime (* 3 60 60))
      (.setMaxPoolSize max-pool-size)
      (.setMinPoolSize min-pool-size))))

(defn create-web-server [db-ctx port join?]
  (jetty/run-jetty (app/get-app db-ctx)
                   {:port port
                    :join? join?}))

(defn start-service [config]
  (let [datasource (create-datasource config)
        db-ctx {:datasource datasource}
        server (create-web-server db-ctx
                                  (-> config :jetty :port)
                                  (-> config :jetty :join?))
        stop (fn []
               (.close datasource)
               (.stop server))]
    stop))

(defn -main []
  (let [cfg (load-config)
        stop-service (start-service cfg)]
    (shutdown-hook (fn []
                     (log/info "Gracefully shutdown, please wait...")
                     (stop-service)))))
