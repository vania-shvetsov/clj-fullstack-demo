(ns patients.app
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.json :as json]
            [ring.middleware.stacktrace :as stacktrace]
            [ring.middleware.gzip :as gzip]
            [ring.logger :as logger]
            [clj-time.format :as tf]
            [cheshire.generate :refer [add-encoder]]
            [compojure.core :refer [GET POST PUT DELETE defroutes] :as compojure]
            [compojure.coercions :refer [as-int]]
            [patients.config :refer [config]]
            [patients.db :as db])
  (:import (org.joda.time DateTime)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dates encoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def date-format (tf/formatter "yyyy-MM-dd"))

(defn api-date-format [d]
  (tf/unparse date-format d))

(add-encoder DateTime
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (api-date-format c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-error-message "Не верное значение")

(def error-messages
  {::ne-string "Значение не может быть пустым"
   :patient/first-name "Имя должно содержать от 2 до 50 символов"
   :patient/middle-name "Отчество должно содержать от 2 до 50 символов"
   :patient/last-name "Отчество должно содержать от 2 до 50 символов"
   :patient/gender "Не верное значение пола"
   :patient/address "Адрес должен содержать от 2 до 150 символов"
   :patient/birth-date "Дата рождения должна быть в формате ГГГГ-ММ-ДД"
   :patient/oms-number "Номер полиса ОМС должен содержать 16 цифр"})

(defn error-msg [problem dict]
  (let [last-spec (-> problem :via peek)
        msg (get dict last-spec)]
    (if msg msg default-error-message)))

(defn validation-errors [spec-result dict]
  (when spec-result
    (into {}
          (map (fn [problem]
                 (let [field (-> problem :in peek name keyword)]
                   [field (error-msg problem dict)]))
               (:clojure.spec.alpha/problems spec-result)))))

(defn validate-and-conform [spec dict value]
  (prn value)
  (let [r (s/conform spec value)]
    (if (= :clojure.spec.alpha/invalid r)
      {:ok false
       :errors (validation-errors (s/explain-data spec value) dict)}
      {:ok true
       :data r})))

(s/def ::string string?)

(s/def ::->ne-string (s/conformer string/trim))

(s/def ::ne-string
  (s/and string? ::->ne-string not-empty))

(s/def ::->date
  (s/and
   ::ne-string
   (s/conformer
    (fn [value]
      (try
        (tf/parse date-format value)
        (catch Exception e
          ::s/invalid))))))

(s/def :patient/name
  (s/and ::ne-string
         (partial re-matches #"[a-zA-Zа-яА-Я]{2,50}")))

(s/def :patient/first-name (s/spec :patient/name))

(s/def :patient/middle-name (s/spec :patient/name))

(s/def :patient/last-name (s/spec :patient/name))

(s/def :patient/gender #{"male" "female"})

(s/def :patient/address
  (s/and ::ne-string
         (partial re-matches #"[\w\s,\.-]{2,150}")))

(s/def :patient/birth-date (s/spec ::->date))

(s/def :patient/oms-number
  (s/and ::ne-string
         (partial re-matches #"\d{16}")))

(s/def ::patient
  (s/keys :req-un [:patient/first-name
                   :patient/middle-name
                   :patient/last-name
                   :patient/gender
                   :patient/address
                   :patient/birth-date
                   :patient/oms-number]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middlewares
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-params [handler]
  (-> handler
      keyword-params/wrap-keyword-params
      params/wrap-params))

(def non-index-uries ["/static" "/api" "/favicon.ico"])

(defn index-route? [uri]
  (not-any? #(string/starts-with? uri %) non-index-uries))

(defn wrap-default-index [handler]
  (fn [request]
    (if (index-route? (:uri request))
      (handler (assoc request :uri "/index.html"))
      (handler request))))

(defn wrap-exception [handler err-prod-response]
  (if (= (:env config) :dev)
    (stacktrace/wrap-stacktrace handler {:color? true})
    (fn [request]
      (try
        (handler request)
        (catch Throwable e
          err-prod-response)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Response helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn success [body]
  {:status 200
   :body body})

(defn client-error
  ([descriptor]
   (client-error 400 descriptor))
  ([status descriptor]
   {:status status
    :body (merge {:error "client_error"} descriptor)}))

(defn client-validation-error [errors]
  (client-error {:error "invalid_data"
                 :data errors}))

(defn bad-request-error []
  (client-error {:error "bad_request"}))

(defn server-error []
  {:status 500
   :headers {"Content-Type" "application/json"}
   :body {:error "server_error"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handler-get-patients [offset limit]
  (let [result (db/get-patients offset limit)]
    (if (db/bad-result? result)
      (bad-request-error)
      (success {:offset offset
                :limit limit
                :total (:total result)
                :data (:data result)}))))


(defn handler-get-patient-by-id [id]
  (let [result (db/get-patient-by-id id)]
    (if (db/bad-result? result)
      (bad-request-error)
      (success {:data result}))))

(defn handler-create-new-patient [patient]
  (let [vr (validate-and-conform ::patient error-messages patient)]
    (if (:ok vr)
      (let [result (db/create-patient! (:data vr))]
        (if (db/bad-result? result)
          (bad-request-error)
          (success {:data {:id result}})))
      (client-validation-error (:errors vr)))))

(defn handler-update-patient [id data]
  (let [vr (validate-and-conform ::patient error-messages data)]
    (if (:ok vr)
      (let [result (db/update-patient! id (:data vr))]
        (if (db/bad-result? result)
          (bad-request-error)
          (success {:data {:id result}})))
      (client-validation-error (:errors vr)))))

(defn handler-delete-patient [id]
  (let [result (db/delete-patient! id)]
    (if (db/bad-result? result)
      (bad-request-error)
      (success {:data {:id result}}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes app-api
  (GET "/patients"
       [offset :<< as-int
        limit :<< as-int]
       (handler-get-patients offset limit))
  (GET "/patients/:id"
       [id :<< as-int]
       (handler-get-patient-by-id id))
  (POST "/patients"
        req
        (handler-create-new-patient (-> req :body)))
  (PUT "/patients/:id"
       [id :<< as-int
        :as req]
       (handler-update-patient id (-> req :body)))
  (DELETE "/patients/:id"
          [id :<< as-int]
          (handler-delete-patient id))
  (fn [_]
    (client-error 404 {:error "not_found"})))

(defroutes app*
  (-> (compojure/context "/api" [] app-api)
      (wrap-exception (server-error))
      (json/wrap-json-response)
      (json/wrap-json-body {:keywords? true})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; App
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def app
  (-> #'app*
      (resource/wrap-resource "public")
      content-type/wrap-content-type
      wrap-default-index
      (logger/wrap-log-request-params {:log-exceptions? false
                                       :transform-fn #(assoc % :level :info)})
      wrap-params
      gzip/wrap-gzip
      (wrap-exception (server-error))))
