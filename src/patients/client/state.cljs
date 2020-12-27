(ns patients.client.state
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [day8.re-frame.http-fx]
            [clojure.string :as string]
            [patients.utils :as utils]))

(def default-response-format (ajax/json-response-format {:keywords? true}))
(def default-request-format (ajax/json-request-format))

(defn in-json [xhrio]
  (merge xhrio
         {:format default-request-format
          :response-format default-response-format}))

(defn ->fullname [{:keys [first-name middle-name last-name]}]
  (str (string/capitalize first-name) " "
       (string/capitalize middle-name) " "
       (string/capitalize last-name)))

(defn patient-form-state []
  {:values
   {:first-name ""
    :middle-name ""
    :last-name ""
    :address ""
    :birth-date ""
    :gender "male"
    :oms-number ""}
   :errors {}})

(def items-per-page 5)

(defn initial-db []
  {:route nil
   :patients []
    ;; {<patient-id> {:open? false}}
   :patient-items {}
   ;; :done :work :error
   :requests {:fetch-patients :done
              :fetch-patient-by-id :done
              :create-patient :done
              :update-patient :done
              :delete-patient :done}
   :current-page 0
   :total-pages 0
   :new-patient-form (patient-form-state)})

(rf/reg-fx
 :confirm
 (fn [{:keys [on-ok on-cancel]}]
   (let [ok? (.confirm js/window )]
     (if ok?
       (when on-ok (rf/dispatch on-ok))
       (when on-cancel (rf/dispatch on-cancel))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Navigation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-db
 :change-route
 (fn [db [_ name params]]
   (assoc db :route {:name name
                     :params params})))

(rf/reg-sub
 :route
 (fn [db _]
   (:route db)))

(rf/reg-fx
 :navigation
 (fn [uri]
   (js/history.pushState nil js/document.title uri)
   (secretary/dispatch! uri)))

(rf/reg-event-fx
 :navto
 (fn [_ [_ uri]] {:navigation uri}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-db
 :initialize
 (fn [_ _] (initial-db)))

(rf/reg-event-fx
 :fetch-patients
 (fn [{:keys [db]} [_ page]]
   (let [offset (* items-per-page page)
         limit items-per-page]
     {:db (assoc-in db [:requests :fetch-patients] :work)
      :http-xhrio (in-json {:method :get
                            :uri "/api/patients"
                            :params {:offset offset
                                     :limit limit}
                            :on-success [:fetch-patients-fulfilled page]
                            :on-failure [:fetch-patients-failure]})})))

(rf/reg-event-db
 :fetch-patients-fulfilled
 (fn [db [_ page response]]
   (let [{:keys [total data]} response]
     (-> db
         (assoc :patient-items {}
                :current-page page
                :total-pages (.ceil js/Math (/ total items-per-page))
                :patients (mapv #(assoc % :fullness :partial) data))
         (assoc-in [:requests :fetch-patients] :done)))))

(rf/reg-event-db
 :fetch-patients-failure
 (fn [db]
   (assoc-in db [:requests :fetch-patients] :error)))

(rf/reg-event-fx
 :fetch-patient-by-id
 (fn [{:keys [db]} [_ id]]
   {:db (assoc-in db [:requests :fetch-patient-by-id] :work)
    :http-xhrio (in-json {:method :get
                          :uri (str "/api/patients/" id)
                          :on-success [:fetch-patient-by-id-fulfilled]
                          :on-failure [:fetch-patient-by-id-failure]})}))

(rf/reg-event-db
 :fetch-patient-by-id-fulfilled
 (fn [db [_ response]]
   (let [{:keys [data]} response]
     (if (some? data)
       (let [patients (:patients db)
             i (utils/find-index #(= (:id %) (:id data)) patients)
             patient (assoc data :fullness :complete)
             patients' (if i
                         (update patients i merge patient)
                         (conj patients patient))]
         (-> db
             (assoc :patients patients')
             (assoc-in [:requests :fetch-patient-by-id] :done)))
       db))))

(rf/reg-event-db
 :fetch-patient-by-id-failure
 (fn [db]
   (assoc-in db [:requests :fetch-patient-by-id] :error)))

(rf/reg-event-fx
 :delete-patient
 (fn [{:keys [db]} [_ id]]
   {:confirm {:on-ok [:delete-patient-continue id]}}))

(rf/reg-event-fx
 :delete-patient-continue
 (fn [{:keys [db]} [_ id]]
   {:db (assoc-in db [:requests :delete-patient] :work)
    :http-xhrio (in-json {:method :delete
                          :uri (str "/api/patients/" id)
                          :on-success [:delete-patient-fulfilled id]
                          :on-failure [:delete-patient-failure]})}))

(rf/reg-event-db
 :delete-patient-fulfilled
 (fn [db [_ id]]
   (-> db
       (assoc-in [:requests :delete-patient] :done)
       (update :patients (fn [v] (into [] (remove #(= (:id %) id) v)))))))


(rf/reg-event-db
 :switch-open-patient-item
 (fn [db [_ id]]
   (update-in db [:patient-items id :open?] not)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :patients
 (fn [db]
   (mapv (fn [x] (assoc x :fullname (->fullname x)))
         (:patients db))))

(rf/reg-sub
 :pagination
 (fn [db]
   {:current (:current-page db)
    :total (:total-pages db)}))

(rf/reg-sub
 :patient-item-open?
 (fn [db [_ id]]
   (get-in db [:patient-items id :open?])))

(comment
  (rf/dispatch [:fetch-patients 2])
  (rf/dispatch [:fetch-patient-by-id 10])
  (rf/dispatch [:delete-patient 9])
  )
