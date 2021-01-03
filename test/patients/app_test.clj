(ns ^:integration patients.app-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [mount.core :as mount]
            [ring.mock.request :refer [request json-body]]
            [cheshire.core :refer [parse-string]]
            [patients.app :as app]
            [patients.db :as db]
            [patients.config :as config]))

(defn fix-mount [t]
  (mount/start #'patients.config/config #'patients.db/db)
  (t)
  (mount/stop))

(defn fix-clean-patients-table [t]
  (t)
  (jdbc/execute! db/db "truncate patients cascade;"))

(use-fixtures :once fix-mount)
(use-fixtures :each fix-clean-patients-table)

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

(def new-patient-full-data
  {:first-name "петр"
   :middle-name "петрович"
   :last-name "петров"
   :birth-date "1990-10-20"
   :address "москва"
   :gender "male"
   :oms-number "0123456789123456"})

(def patient-short-data
  (select-keys new-patient-full-data [:first-name
                                      :middle-name
                                      :last-name]))

(deftest test-health-check
  (is (= (response-200-expected "")
         (app/app (request :get "/api/health-check")))))

(deftest test-get-patients-empty-ok
  (is (= (response-200-expected {:offset 0
                                 :limit 5
                                 :total 0
                                 :data []})
         (-> (request :get "/api/patients?offset=0&limit=5")
             app/app
             parse-body))))

(deftest test-get-patients-non-empty-ok
  (let [response (-> (request :post "/api/patients")
                     (json-body new-patient-full-data)
                     app/app
                     parse-body)
        id (get-in response [:body :data :id])]
    (is (= (response-200-expected {:offset 0
                                   :limit 5
                                   :total 1
                                   :data [(assoc patient-short-data :id id)]})
           (-> (request :get "/api/patients?offset=0&limit=5")
               app/app
               parse-body)))))

(deftest test-get-patients-400
  (is (= (response-4xx-expected {:error "bad_request"} 400)
         (-> (request :get "/api/patients?offset=0&limit=-1")
             app/app
             parse-body))))

(deftest test-create-new-patient-ok
  (let [response (-> (request :post "/api/patients")
                     (json-body new-patient-full-data)
                     app/app
                     parse-body)
        id (get-in response [:body :data :id])]
    (is (= (type id) java.lang.Integer))))

(deftest test-create-new-patient-validation-error
  (let [response (-> (request :post "/api/patients")
                     (json-body (assoc new-patient-full-data :first-name ""))
                     app/app
                     parse-body)
        first-name-error (get-in response [:body :data :first-name])]
    (is (= (type first-name-error) java.lang.String))))

(deftest test-get-patient-by-id
  (testing "get existing patient"
    (let [response (-> (request :post "/api/patients")
                       (json-body new-patient-full-data)
                       app/app
                       parse-body)
          id (get-in response [:body :data :id])]
      (is (= (response-200-expected {:data (assoc new-patient-full-data :id id)})
             (-> (request :get (str "/api/patients/" id))
                 app/app
                 parse-body
                 (update-in [:body :data] dissoc :created-at))))))

  (testing "get non existing patient"
    (is (= (response-200-expected {:data nil})
           ;;
           (-> (request :get (str "/api/patients/" 0))
               app/app
               parse-body)))))

(deftest test-put-patient-ok
  (let [new-patient-response (-> (request :post "/api/patients")
                                 (json-body new-patient-full-data)
                                 app/app
                                 parse-body)
        id (get-in new-patient-response [:body :data :id])
        updated-patient (assoc new-patient-full-data
                               :id id
                               :oms-number "0987654321654321")]
    (is (= (response-200-expected {:data {:id id}})
           (-> (request :put (str "/api/patients/" id))
               (json-body updated-patient)
               app/app
               parse-body)))))

(deftest test-delete-patient-ok
  (let [new-patient-response (-> (request :post "/api/patients")
                                 (json-body new-patient-full-data)
                                 app/app
                                 parse-body)
        id (get-in new-patient-response [:body :data :id])]
    (is (= (response-200-expected {:data {:id id}})
           (-> (request :delete (str "/api/patients/" id))
               app/app
               parse-body)))
    (is (= (response-200-expected {:data nil})
           (-> (request :get (str "/api/patients/" id))
               app/app
               parse-body)))))

(deftest test-404
  (is (= (response-4xx-expected {:error "not_found"} 404)
         (-> (request :get "/api/unknown-endpoint")
             app/app
             parse-body))))
