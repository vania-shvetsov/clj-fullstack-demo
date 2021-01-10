(ns ^:integration patients.app-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [clojure.java.jdbc :as jdbc]
            [mount.core :as mount]
            [ring.mock.request :refer [request json-body]]
            [cheshire.core :refer [parse-string]]
            [patients.app :as app]
            [patients.db :as db]
            [patients.config :as config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fix-mount [t]
  (mount/start #'patients.config/config #'patients.db/db)
  (t)
  (mount/stop))

(defn fix-clean-patients-table [t]
  (t)
  (jdbc/execute! db/db "truncate patients cascade;"))

(use-fixtures :once fix-mount)
(use-fixtures :each fix-clean-patients-table)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-body [res]
  (update res :body parse-string true))

(defn response-200-expected [body]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body body})

(defn response-4xx-expected [body status]
  {:status status
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body body})

(defn req-to-app [req]
  (-> req app/app parse-body))

(def patient-full-data
  {:first-name "петр"
   :middle-name "петрович"
   :last-name "петров"
   :birth-date "1990-10-20"
   :address "москва"
   :gender "male"
   :oms-number "0123456789123456"})

(def patient-short-data
  (select-keys patient-full-data [:first-name
                                  :middle-name
                                  :last-name]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-health-check
  (is (= (response-200-expected "")
         (app/app (request :get "/api/health-check")))))

(deftest test-get-patients-empty-ok
  (is (= (response-200-expected {:offset 0
                                 :limit 5
                                 :total 0
                                 :data []})
         (-> (request :get "/api/patients" {:offset 0
                                            :limit 5})
             (req-to-app)))))

(deftest test-get-patients-non-empty-ok
  (let [response (-> (request :post "/api/patients")
                     (json-body patient-full-data)
                     req-to-app)
        id (get-in response [:body :data :id])]
    (is (= (response-200-expected {:offset 0
                                   :limit 5
                                   :total 1
                                   :data [(assoc patient-short-data :id id)]})
           (-> (request :get "/api/patients" {:offset 0
                                              :limit 5})
               req-to-app)))))

(deftest test-get-patients-400
  (is (= (response-4xx-expected {:error "bad_request"} 400)
         (-> (request :get "/api/patients" {:offset 0
                                            :limit -1})
             req-to-app))))

(deftest test-create-new-patient-ok
  (let [response (-> (request :post "/api/patients")
                     (json-body patient-full-data)
                     req-to-app)
        id (get-in response [:body :data :id])]
    (is (= (type id) java.lang.Integer))))

(deftest test-create-new-patient-validation-error
  (let [response (-> (request :post "/api/patients")
                     (json-body (assoc patient-full-data :first-name ""))
                     req-to-app)
        first-name-error (get-in response [:body :data :first-name])]
    (is (= (type first-name-error) java.lang.String))))

(deftest test-get-patient-by-id
  (testing "get existing patient"
    (let [response (-> (request :post "/api/patients")
                       (json-body patient-full-data)
                       req-to-app)
          id (get-in response [:body :data :id])]
      (is (= (response-200-expected {:data (assoc patient-full-data :id id)})
             (-> (request :get (str "/api/patients/" id))
                 req-to-app
                 (update-in [:body :data] dissoc :created-at))))))

  (testing "get non existing patient"
    (is (= (response-200-expected {:data nil})
           ;;
           (-> (request :get (str "/api/patients/" 0))
               req-to-app)))))

(deftest test-put-patient-ok
  (let [new-patient-response (-> (request :post "/api/patients")
                                 (json-body patient-full-data)
                                 req-to-app)
        id (get-in new-patient-response [:body :data :id])
        updated-patient (assoc patient-full-data
                               :id id
                               :oms-number "0987654321654321")]
    (is (= (response-200-expected {:data {:id id}})
           (-> (request :put (str "/api/patients/" id))
               (json-body updated-patient)
               req-to-app)))))

(deftest test-delete-patient-ok
  (let [new-patient-response (-> (request :post "/api/patients")
                                 (json-body patient-full-data)
                                 req-to-app)
        id (get-in new-patient-response [:body :data :id])]
    (testing "return id of deleted patient"
      (is (= (response-200-expected {:data {:id id}})
             (-> (request :delete (str "/api/patients/" id))
                 req-to-app))))
    (testing "get nil for deleted patient"
      (is (= (response-200-expected {:data nil})
             (-> (request :get (str "/api/patients/" id))
                 req-to-app))))))

(deftest test-404
  (is (= (response-4xx-expected {:error "not_found"} 404)
         (-> (request :get "/api/unknown-endpoint")
             req-to-app))))
