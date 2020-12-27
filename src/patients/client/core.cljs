(ns ^:figwheel-hooks patients.client.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [secretary.core :refer [defroute]:as secretary]
            [goog.events :as events]
            [patients.client.state :as state]
            [patients.client.views :as views])
  (:import [goog.history Html5History]
           [goog.history EventType]))

(defroute "/" []
  (rf/dispatch [:change-route :patient-list]))

(defroute "/new-patient" []
  (rf/dispatch [:change-route :new-patient]))

(defroute edit-patient "/edit-patient/:id" [id]
  (rf/dispatch [:change-route :edit-patient {:id id}]))

(defroute "*" []
  (rf/dispatch [:change-route :not-found]))

(def route-table
  {:patient-list views/page-patients
   :new-patient views/page-new-patient
   :edit-patient views/page-edit-patient
   :not-found views/page-not-found})

(defn router []
  (let [route @(rf/subscribe [:route])]
    (when route
      (let [{:keys [name params]} route]
        [(get route-table name) params]))))

(defn render []
  (rdom/render [#'views/app router] (.querySelector js/document "#app")))

(defn ^:export run-app []
  (rf/dispatch-sync [:initialize])
  (render))

(defn ^:after-load re-render []
  (render))

(defn run-browser-navigation! []
  (doto (Html5History.)
    (events/listen EventType/NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setUseFragment false)
    (.setPathPrefix "")
    (.setEnabled true)))

(run-browser-navigation!)
