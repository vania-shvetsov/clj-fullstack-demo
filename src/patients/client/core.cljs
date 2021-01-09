(ns ^:figwheel-hooks patients.client.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [secretary.core :refer [defroute] :as secretary]
            [goog.events :as events]
            [patients.client.state]
            [patients.client.views :as views])
  (:import [goog.history Html5History]
           [goog.history EventType]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroute "/" []
  (rf/dispatch [:navigation/set-route :patient-list]))

(defroute "/new-patient" []
  (rf/dispatch [:navigation/set-route :new-patient]))

(defroute #"/edit-patient/(\d+)" [id]
  (rf/dispatch [:navigation/set-route :edit-patient {:id (int id)}]))

(defroute "*" []
  (rf/dispatch [:navigation/set-route :not-found]))

(defn run-browser-navigation! []
  (doto (Html5History.)
    (events/listen EventType/NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setUseFragment false)
    (.setPathPrefix "")
    (.setEnabled true)))

(run-browser-navigation!)

(def route-table
  {:patient-list views/page-patients
   :new-patient views/page-new-patient
   :edit-patient views/page-edit-patient
   :not-found views/page-not-found})

(defn router []
  (when-let [route @(rf/subscribe [:navigation/current-route])]
    [(route-table (:name route))
     (:params route)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Render
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render []
  (rdom/render [#'views/app router]
               (.querySelector js/document "#app")))

(defn ^:export run-app []
  (rf/dispatch-sync [:init-db])
  (render))

(defn ^:after-load re-render []
  (render))
