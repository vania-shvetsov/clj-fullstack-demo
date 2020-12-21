(ns patients.db
  (:require [clojure.java.jdbc :as jdbc]
            [mount.core :refer [defstate]]
            [patients.config :refer [config]]
            [honeysql.core :as sql])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :dbtype "postgresql"
              :host (-> config :db :host)
              :port (-> config :db :port)
              :dbname (-> config :db :dbname)
              :user (-> config :db :user)
              :password (-> config :db :password)})

(defn jdbc-url [spec]
  (format "jdbc:%s://%s:%s/%s"
          (:subprotocol spec)
          (:host spec)
          (:port spec)
          (:dbname spec)))

(defn datasource [spec]
  (doto (ComboPooledDataSource.)
    (.setDriverClass (:classname spec))
    (.setJdbcUrl (format "jdbc:%s://%s:%s/%s"
                         (:subprotocol spec)
                         (:host spec)
                         (:port spec)
                         (:dbname spec)))
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

(def db {:datasource ds})

(comment
  ;; Check db connection
  (jdbc/query db ["select 3*5 as result"])
  )
