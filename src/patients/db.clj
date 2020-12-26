(ns patients.db
  (:require [mount.core :refer [defstate]]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as hh]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [clojure.tools.logging :as log]
            [patients.utils :refer [->kebab-case-string]]
            [patients.config :refer [config]])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro ->sql [& subs]
  `(-> ~@subs
       sql/format))

(defmacro safe-query [& body]
  `(try
     ~@body
     (catch java.sql.SQLException e#
       (log/error (.getMessage e#))
       :db-error)))

(defn bad-result? [result]
  (= result :db-error))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-patients [offset limit]
  (safe-query
   (jdbc/with-db-transaction [c db]
     (let [data (jdbc/query c
                            (->sql (hh/select :id
                                              :created-at
                                              :first-name
                                              :middle-name
                                              :last-name
                                              :birth-date)
                                   (hh/from :patients)
                                   (hh/order-by :created-at)
                                   (hh/offset offset)
                                   (hh/limit limit))
                            {:identifiers ->kebab-case-string})
           total (jdbc/query c (->sql (hh/select :%count.id)
                                      (hh/from :patients)))]
       {:data (doall data)
        :total (-> total first :count)}))))

(defn get-patient-by-id [patient-id]
  (safe-query
   (let [data (jdbc/query db
                          (->sql (hh/select :*)
                                 (hh/from :patients)
                                 (hh/where [:= :id patient-id]))
                          {:identifiers ->kebab-case-string})]
     (first data))))

(defn create-patient! [patient]
  (safe-query
   (let [data (jdbc/execute! db
                             (->sql (hh/insert-into :patients)
                                    (hh/values [patient]))
                             {:return-keys ["id"]})]
     (:id data))))

(defn update-patient! [id patient]
  (safe-query
   (let [data (jdbc/execute! db
                             (->sql (hh/update :patients)
                                    (hh/sset patient)
                                    (hh/where [:= :id id]))
                             {:return-keys ["id"]})]
     (:id data))))

(defn delete-patient! [id]
  (safe-query
   (let [data (jdbc/execute! db
                             (->sql (hh/delete-from :patients)
                                    (hh/where [:= :id id]))
                             {:return-keys ["id"]})]
     (:id data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (get-patient-by-id 3)

  (get-patients 0 5)

  (create-patient! {:first-name "Василий"
                    :middle-name "Васильевич"
                    :last-name "Васильев"
                    :gender "male"
                    :birth-date (t/date-time 1999 5 23)
                    :address "Moscow"
                    :oms-number "1234567890123456"})

  (update-patient! 4 {:first-name "Андрей"
                      :middle-name "Андреевич"
                      :last-name "Андреев"
                      :gender "male"
                      :birth-date (t/date-time 1980 1 15)
                      :address "Moscow"
                      :oms-number "1234567890123456"})

  (delete-patient! 5)
  )
