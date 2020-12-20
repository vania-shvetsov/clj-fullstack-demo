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
            [compojure.core :refer [GET POST PUT DELETE defroutes] :as compojure]
            [compojure.coercions :refer [as-int]]
            [clojure.pprint :as pp]))

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
      (let [response (handler (assoc request :uri "/index.html"))]
        (assoc-in response [:headers "Content-Type"] "text/html"))
      (handler request))))

;; Handlers

(defn handler-get-patients [offset limit]
  {:status 200
   :body {:offset offset
          :limit limit
          :data [{:id 0 :name "vanya"}
                 {:id 1 :name "petya"}]}})

(defn handler-get-patient-by-id [id]
  {:status 200
   :body {:data {:id 0 :name "vanya"}}})

(defn handler-create-new-patient [patient]
  {:status 200
   :body {:data {:id 0}}})

(defn handler-update-patient [id data]
  {:status 200
   :body {:data {:id id}}})

(defn handler-delete-patient [id]
  {:status 200
   :body {:data "ok"}})

;; Routes

(defroutes api
  (GET "/api/patients"
       [offset :<< as-int
        limit :<< as-int]
       (handler-get-patients offset limit))
  (GET "/api/patients/:id"
       [id :<< as-int]
       (handler-get-patient-by-id id))
  (POST "/api/patients"
        req
        (handler-create-new-patient (-> req :body :data)))
  (PUT "/api/patients/:id"
       [id :<< as-int
        :as req]
       (handler-update-patient id (-> req :body :data)))
  (DELETE "/api/patients/:id"
          [id :<< as-int]
          (handler-delete-patient id)))

(defroutes app-raw
  (-> api
      (json/wrap-json-response)
      (json/wrap-json-body {:keywords? true})))

;; App

(def app
  (-> #'app-raw
      (resource/wrap-resource "public")
      wrap-default-index
      content-type/wrap-content-type
      (logger/wrap-log-request-params {:log-exceptions? false
                                       :transform-fn #(assoc % :level :info)})
      wrap-params
      gzip/wrap-gzip
      (stacktrace/wrap-stacktrace {:color? true})))
