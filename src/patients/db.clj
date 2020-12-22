(ns patients.db
  (:require [mount.core :refer [defstate]]
            [clojure.java.jdbc :as jdbc]
            [patients.config :refer [config]]
            [honeysql.core :as sql])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn jdbc-url [spec]
  (format "jdbc:%s:%s"
          (:subprotocol spec)
          (:subname spec)))

(defn datasource [spec]
  (doto (ComboPooledDataSource.)
    (.setDriverClass (-> spec :db :classname))
    (.setJdbcUrl (jdbc-url spec))
    (.setUser (-> spec :db :user))
    (.setPassword (-> spec :db :password))
    (.setMaxIdleTimeExcessConnections (* 30 60))
    (.setMaxIdleTime (* 3 60 60))
    (.setMaxPoolSize (-> spec :db-pool :max-pool-size))
    (.setMinPoolSize (-> spec :db-pool :min-pool-size))))

(defstate db
  :start
  {:datasource (datasource config)}
  :stop
  (.close (:datasource db)))

(comment
  ;; Check db connection
  (jdbc/query db ["select 3*5 as result"])
  )
