(ns patients.db
  (:require [mount.core :refer [defstate]]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as hh]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [clojure.tools.logging :as log]
            [patients.config :refer [config]])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn jdbc-url [c]
  (format "jdbc:%s:%s"
          (:subprotocol c)
          (:subname c)))

(defn datasource [c]
  (let [classname (-> c :db :classname)
        url (jdbc-url (:db c))
        user (-> c :db :user)
        password (-> c :db :password)
        max-pool-size (-> c :db-pool :max-pool-size)
        min-pool-size (-> c :db-pool :min-pool-size)]
    (println classname url user password max-pool-size min-pool-size)
    (doto (ComboPooledDataSource.)
      (.setDriverClass classname)
      (.setJdbcUrl url)
      (.setUser user)
      (.setPassword password)
      (.setMaxIdleTimeExcessConnections (* 30 60))
      (.setMaxIdleTime (* 3 60 60))
      (.setMaxPoolSize max-pool-size)
      (.setMinPoolSize min-pool-size))))

(defstate db
  :start
  {:datasource (datasource config)}
  :stop
  (.close (:datasource db)))

(comment
  ;; Check db connection
  (jdbc/query db ["select 3*5 as result"])
  )

;; Queries helpers

(defn sql-insert [table entity]
  (-> (hh/insert-into table)
      (hh/values [entity])
      sql/format))

(defn sql-update [table id entity]
  (-> (hh/update table)
      (hh/sset entity)
      (hh/where [:= :id id])
      sql/format))

(defn sql-delete [table id]
  (-> (hh/delete-from table)
      (hh/where [:= :id id])
      sql/format))

(defn sql-get [s]
  (sql/format s))

(defn execute! [db sql-fn & args]
  (let [s (apply sql-fn args)]
    (try
      (let [result (jdbc/execute! db s {:return-keys ["id"]})]
        (:id result))
      (catch Exception e
        (log/error (.getMessage e))
        :bad-query))))

(defn insert-entity! [db table entity]
  (execute! db sql-insert table entity))

(defn update-entity! [db table id entity]
  (execute! db sql-update table id entity))

(defn delete-entity! [db table id]
  (execute! db sql-delete table id))

(defn fetch-entities [db s]
  (try
    (jdbc/query db (sql-get s))
    (catch Exception e
      (log/error (.getMessage e))
      :bad-query)))

;; Queries

(defn get-patient-by-id [patient-id]
  (->
   (fetch-entities db (-> (hh/select :*)
                          (hh/from :patients)
                          (hh/where [:= :id patient-id])))
   first))

(defn get-all-patients [offset limit]
  (fetch-entities db (-> (hh/select :*)
                         (hh/from :patients)
                         (hh/order-by :created-at)
                         (hh/offset offset)
                         (hh/limit limit))))

(defn create-patient! [patient]
  (insert-entity! db :patients patient))

(defn update-patient! [id patient]
  (update-entity! db :patients id patient))

(defn delete-patient! [patient-id]
  (delete-entity! db :patients patient-id))


(defn bad-query? [result]
  (= result :bad-query))

(comment
  (get-patient-by-id 3)

  (get-all-patients 10 -1)

  (create-patient! {:first-name "Василий"
                    :middle-name "Васильевич"
                    :last-name "Васильев"
                    :gender "male"
                    :birth-date (t/date-time 1999 5 23)
                    :address "Moscow"
                    :oms-number "1234567890123456"})

  (update-patient! 3 {:first-name "Андрей"
                    :middle-name "Андреевич"
                    :last-name "Андреев"
                    :gender "male"
                    :birth-date (t/date-time 1980 1 15)
                    :address "Moscow"
                    :oms-number "1234567890123456"})

  (delete-patient! 2)
  )
