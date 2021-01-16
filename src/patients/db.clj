(ns patients.db
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as hh]
            [clj-time.jdbc]
            [clojure.tools.logging :as log]
            [patients.utils :refer [->kebab-case-string]]))

(java.util.TimeZone/setDefault (java.util.TimeZone/getTimeZone "GMT"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query helpers
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

(defn get-patients [ctx offset limit]
  (safe-query
   (jdbc/with-db-transaction [c ctx]
     (let [data (jdbc/query c
                            (->sql (hh/select :id
                                              :first-name
                                              :middle-name
                                              :last-name)
                                   (hh/from :patients)
                                   (hh/order-by [:created-at :desc])
                                   (hh/offset offset)
                                   (hh/limit limit))
                            {:identifiers ->kebab-case-string})
           total (jdbc/query c (->sql (hh/select :%count.id)
                                      (hh/from :patients)))]
       #_(Thread/sleep 3000)
       {:data (doall data)
        :total (-> total first :count)}))))

(defn get-patient-by-id [db patient-id]
  (safe-query
   (let [data (jdbc/query db
                          (->sql (hh/select :*)
                                 (hh/from :patients)
                                 (hh/where [:= :id patient-id]))
                          {:identifiers ->kebab-case-string})]
     #_(Thread/sleep 3000)
     (first data))))

(defn create-patient! [ctx patient]
  (safe-query
   (let [data (jdbc/execute! ctx
                             (->sql (hh/insert-into :patients)
                                    (hh/values [patient]))
                             {:return-keys ["id"]})]
     (:id data))))

(defn update-patient! [ctx id patient]
  (safe-query
   (let [data (jdbc/execute! ctx
                             (->sql (hh/update :patients)
                                    (hh/sset patient)
                                    (hh/where [:= :id id]))
                             {:return-keys ["id"]})]
     #_(Thread/sleep 3000)
     (:id data))))

(defn delete-patient! [ctx id]
  (safe-query
   (let [data (jdbc/execute! ctx
                             (->sql (hh/delete-from :patients)
                                    (hh/where [:= :id id]))
                             {:return-keys ["id"]})]
     (:id data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (comment
;;   (jdbc/query db ["select 3*5 as result"])

;;   (get-patient-by-id 3)

;;   (get-patients 0 5)

;;   (create-patient! {:first-name "Василий"
;;                     :middle-name "Васильевич"
;;                     :last-name "Васильев"
;;                     :gender "male"
;;                     :birth-date (t/date-time 1999 5 23)
;;                     :address "Moscow"
;;                     :oms-number "1234567890123456"})

;;   (update-patient! 4 {:first-name "Андрей"
;;                       :middle-name "Андреевич"
;;                       :last-name "Андреев"
;;                       :gender "male"
;;                       :birth-date (t/date-time 1980 1 15)
;;                       :address "Moscow"
;;                       :oms-number "1234567890123456"})

;;   (delete-patient! 5))
