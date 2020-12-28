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

(defn calc-page [items-count per-page]
  (.ceil js/Math (/ items-count per-page)))

(def items-per-page 4)

(defn initial-db []
  {:route nil
   :patients []
   ;; {<patient-id> {:open? false
   ;;                :load? false}}
   :patient-items {}
   ;; :done :work :error
   :requests {:fetch-patients :done
              :fetch-patient-by-id :done
              :create-patient :done
              :update-patient :done
              :delete-patient :done}
   :offset 0
   :total 0})

(rf/reg-fx
 :confirm
 (fn [{:keys [on-ok on-cancel message]}]
   (let [ok? (.confirm js/window message)]
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
                :offset (* items-per-page page)
                :total total
                :patients (mapv #(assoc % :fullness :partial) data))
         (assoc-in [:requests :fetch-patients] :done)))))

(rf/reg-event-db
 :fetch-patients-failure
 (fn [db]
   (assoc-in db [:requests :fetch-patients] :error)))

(rf/reg-event-fx
 :fetch-patient-by-id
 (fn [{:keys [db]} [_ id]]
   {:db (-> db
            (assoc-in [:requests :fetch-patient-by-id] :work)
            (assoc-in [:patient-items id :loading?] true))
    :http-xhrio (in-json {:method :get
                          :uri (str "/api/patients/" id)
                          :on-success [:fetch-patient-by-id-fulfilled]
                          :on-failure [:fetch-patient-by-id-failure id]})}))

(rf/reg-event-db
 :fetch-patient-by-id-fulfilled
 (fn [db [_ response]]
   (let [{:keys [data]} response
         id (:id data)]
     (if (some? data)
       (let [patients (:patients db)
             i (utils/find-index #(= (:id %) id) patients)
             patient (assoc data :fullness :complete)
             patients' (if i
                         (update patients i merge patient)
                         (conj patients patient))]
         (-> db
             (assoc :patients patients')
             (assoc-in [:requests :fetch-patient-by-id] :done)
             (assoc-in [:patient-items id :loading?] false)))
       db))))

(rf/reg-event-db
 :fetch-patient-by-id-failure
 (fn [db [_ id]]
   (-> db
       (assoc-in [:requests :fetch-patient-by-id] :error)
       (assoc-in [:patient-items id :loading?] false))))

(rf/reg-event-fx
 :delete-patient
 (fn [{:keys [db]} [_ id index]]
   {:confirm {:message "Удалить данные о пациенте?"
              :on-ok [:delete-patient-continue id index]}}))

(rf/reg-event-fx
 :delete-patient-continue
 (fn [{:keys [db]} [_ id index]]
   {:db (assoc-in db [:requests :delete-patient] :work)
    :http-xhrio (in-json {:method :delete
                          :uri (str "/api/patients/" id)
                          :on-success [:delete-patient-fulfilled id index]
                          :on-failure [:delete-patient-failure]})}))

(rf/reg-event-fx
 :delete-patient-fulfilled
 (fn [{:keys [db]} [_ id index]]
   (let [{:keys [total offset]} db
         total' (dec total)
         ;; If it was the last item on current page
         need-to-change-offset? (<= total' offset)
         offset' (if need-to-change-offset?
                     (max 0 (- offset items-per-page))
                     offset)
         fx
         {:db (-> db
                  (assoc :total total'
                         :offset offset')
                  (assoc-in [:requests :delete-patient] :done)
                  (update :patients (fn [v] (into [] (remove #(= (:id %) id) v)))))}]

     (if need-to-change-offset?
       (assoc fx :dispatch [:fetch-patients (calc-page offset' items-per-page)])
       fx))))

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
   {:current (calc-page (:offset db) items-per-page)
    :total (calc-page (:total db) items-per-page)}))

(rf/reg-sub
 :patient-item-open?
 (fn [db [_ id]]
   (get-in db [:patient-items id :open?])))

(rf/reg-sub
 :patient-item-loading?
 (fn [db [_ id]]
   (boolean (get-in db [:patient-items id :loading?]))))

(rf/reg-sub
 :request-status
 (fn [db [_ request-name]]
   (get-in db [:requests request-name])))

(rf/reg-sub
 :request-work?
 (fn [db [_ request-name]]
   (= (get-in db [:requests request-name])
      :work)))

(comment
  (rf/dispatch [:fetch-patients 2])
  (rf/dispatch [:fetch-patient-by-id 10])
  (rf/dispatch [:delete-patient 9])
  )
