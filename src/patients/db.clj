(ns patients.db
  (:require [clojure.java.jdbc :as jdbc]
            [mount.core :refer [defstate]]
            [patients.config :refer [config]]
            [honeysql.core :as sql])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :dbtype "postgresql"
              :subname (format "//%s:%s/%s"
                               (-> config :db :host)
                               (-> config :db :port)
                               (-> config :db :dbname))
              :user (-> config :db :user)
              :password (-> config :db :password)})

(defn jdbc-url [spec]
  (format "jdbc:%s:%s"
          (:subprotocol spec)
          (:subname spec)))

(defn datasource [spec]
  (doto (ComboPooledDataSource.)
    (.setDriverClass (:classname spec))
    (.setJdbcUrl (jdbc-url spec))
    (.setUser (:user spec))
    (.setPassword (:password spec))
    (.setMaxIdleTimeExcessConnections (* 30 60))
    (.setMaxIdleTime (* 3 60 60))
    (.setMaxPoolSize 10)
    (.setMinPoolSize 10)))

(defstate ds
  :start
  (datasource db-spec)
  :stop
  (.close ds))

(defstate db
  :start
  {:datasource ds})

(comment
  ;; Check db connection
  (jdbc/query db ["select 3*5 as result"])
  )
