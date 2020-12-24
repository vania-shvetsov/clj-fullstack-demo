(ns patients.app
  (:require [clojure.string :as string]
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
            [patients.db :as db]
            [patients.utils :as utils])
  (:import (org.joda.time DateTime)))

;; Dates encoding

(def api-date-format (tf/formatter "yyyy-MM-dd"))

(defn format-to-api-date [d]
  (tf/unparse api-date-format d))

(add-encoder DateTime
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (format-to-api-date c))))


;; Middlewares

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


;; Handlers

(defn success [body]
  {:status 200
   :body body})

(defn client-error
  ([error]
   (client-error error 400))
  ([error status]
   {:status status
    :body {:error-type "client_error"
           :error-details error}}))

(defn server-error []
  {:status 500
   :headers {"Content-Type" "application/json"}
   :body {:error "server_error"}})


(defn handler-get-patients [offset limit]
  (let [result (db/get-all-patients offset limit)]
    (if (db/bad-query? result)
      (client-error "Bad request")
      (success {:offset offset
                :limit limit
                :data result}))))

(defn handler-get-patient-by-id [id]
  (let [result (db/get-patient-by-id id)]
    (if (db/bad-query? result)
      (client-error "Bad request")
      (success {:data result}))))

(defn handler-create-new-patient [patient]
  (let [result (db/create-patient! patient)]
    (if (db/bad-query? result)
      (client-error "Bad request")
      (success {:data {:id result}}))))

(defn handler-update-patient [id data]
  (let [result (db/update-patient! id data)]
    (if (db/bad-query? result)
      (client-error "Bad request")
      (success {:data {:id result}}))))

(defn handler-delete-patient [id]
  (let [result (db/delete-patient! id)]
    (if (db/bad-query? result)
      (client-error "Bad request")
      (success {:data {:id result}}))))

;; Routes

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
    (client-error "Not found" 404)))

(defroutes app*
  (-> (compojure/context "/api" [] app-api)
      (wrap-exception (server-error))
      (json/wrap-json-body {:keywords? true})
      (json/wrap-json-response {:key-fn utils/->snake-case-string})))

;; App

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
