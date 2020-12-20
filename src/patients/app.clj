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
            [patients.config :refer [config]]))

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
        (handler-create-new-patient (-> req :body :data)))
  (PUT "/patients/:id"
       [id :<< as-int
        :as req]
       (handler-update-patient id (-> req :body :data)))
  (DELETE "/patients/:id"
          [id :<< as-int]
          (handler-delete-patient id)))

(defroutes app*
  (-> (compojure/context "/api" [] app-api)
      (wrap-exception {:status 500
                       :headers {"Content-Type" "application/json"}
                       :body {:error "server_error"}})
      (json/wrap-json-body {:keywords? true})
      json/wrap-json-response))

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
      (wrap-exception {:status 500
                       :headers {"Content-Type" "text/html"}
                       :body "Inner error"})))
