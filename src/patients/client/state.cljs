(ns patients.client.state
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [fork.re-frame :as fork]
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

   :pages {:patients {:data {:offset 0
                             :total 0
                             :patients []}
                      :ui {:items {}}
                      :process {:request-fetch-patients :init
                                :request-fetch-patient-by-id :init
                                :request-delete-patient :init}}

           :edit-patient {:data {:patient nil}
                          :process {}}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common fx
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-fx
 :dialog/confirm
 (fn [{:keys [on-ok on-cancel message]}]
   (let [ok? (.confirm js/window message)]
     (if ok?
       (when on-ok (rf/dispatch on-ok))
       (when on-cancel (rf/dispatch on-cancel))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Navigation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-fx
 :navigation/_to
 (fn [uri]
   (js/history.pushState nil js/document.title uri)
   (secretary/dispatch! uri)))

(rf/reg-fx
 :navigation/_back
 (fn []
   (js/history.back)))


(rf/reg-event-db
 :navigation/set-route
 (fn [db [_ name params]]
   (assoc db :route {:name name
                     :params params})))

(rf/reg-event-fx
 :navigation/to
 (fn [_ [_ uri]]
   {:navigation/_to uri}))

(rf/reg-event-fx
 :navigation/back
 (fn [_ [_]]
   {:navigation/_back []}))


(rf/reg-sub
 :navigation/current-route
 (fn [db _]
   (:route db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-db
 :init-db
 (fn [_ _] (initial-db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page "patients"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Event handlers
;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-fx
 :patients/load-initial-patients
 [(rf/path :pages :patients)]
 (fn [_ _]
   {:dispatch [:patients/load-n-page-patients 0]}))

(rf/reg-event-fx
 :patients/load-n-page-patients
 [(rf/path :pages :patients)]
 (fn [{:keys [db]} [_ page]]
   (let [offset (* items-per-page page)
         limit items-per-page]
     {:db (-> db
              (assoc-in [:process :request-fetch-patients] :work)
              (assoc-in [:ui :items] {}))
      :http-xhrio (in-json {:method :get
                            :uri "/api/patients"
                            :params {:offset offset
                                     :limit limit}
                            :on-success [:patients/_fetch-patients-ok page]
                            :on-failure [:patients/_fetch-patients-err]})})))

(rf/reg-event-db
 :patients/_fetch-patients-ok
 [(rf/path :pages :patients)]
 (fn [db [_ page response]]
   (let [{:keys [total data]} response]
     (-> db
         (update :data assoc
                 :offset (* items-per-page page)
                 :total total
                 :patients (mapv #(assoc % :fullness :partial) data))
         (assoc-in [:process :request-fetch-patients] :done)))))

(rf/reg-event-db
 :patients/_fetch-patients-err
 [(rf/path :pages :patients)]
 (fn [db]
   (assoc-in db [:process :request-fetch-patients] :error)))

(rf/reg-event-fx
 :patients/load-patient-details
 [(rf/path :pages :patients)]
 (fn [{:keys [db]} [_ id]]
   {:db (-> db
            (assoc-in [:process :request-fetch-patient-by-id] :work)
            (assoc-in [:ui :items id :loading?] true))
    :http-xhrio (in-json {:method :get
                          :uri (str "/api/patients/" id)
                          :on-success [:patients/_fetch-patient-by-id-ok]
                          :on-failure [:patients/_fetch-patient-by-id-err id]})}))

(rf/reg-event-db
 :patients/_fetch-patient-by-id-ok
 [(rf/path :pages :patients)]
 (fn [db [_ response]]
   (let [{:keys [data]} response
         id (:id data)]
     (if (some? data)
       (let [patients (get-in db [:data :patients])
             i (utils/find-index #(= (:id %) id) patients)
             patient (assoc data :fullness :complete)
             patients' (if i
                         (update patients i merge patient)
                         (conj patients patient))]
         (-> db
             (assoc-in [:data :patients] patients')
             (assoc-in [:process :request-fetch-patient-by-id] :done)
             (assoc-in [:ui :items id :loading?] false)))
       db))))

(rf/reg-event-db
 :patients/_fetch-patient-by-id-err
 [(rf/path :pages :patients)]
 (fn [db [_ id]]
   (-> db
       (assoc-in [:process :request-fetch-patient-by-id] :error)
       (assoc-in [:ui :items id :loading?] false))))

(rf/reg-event-fx
 :patients/try-to-delete-patient
 (fn [{:keys [db]} [_ id]]
   {:dialog/confirm {:message "Удалить данные о пациенте?"
                     :on-ok [:patients/_actual-delete-patient id]}}))

(rf/reg-event-fx
 :patients/_actual-delete-patient
 [(rf/path :pages :patients)]
 (fn [{:keys [db]} [_ id]]
   {:db (assoc-in db [:process :request-delete-patient] :work)
    :http-xhrio (in-json {:method :delete
                          :uri (str "/api/patients/" id)
                          :on-success [:patients/_delete-patient-ok id]})}))

(rf/reg-event-fx
 :patients/_delete-patient-ok
 [(rf/path :pages :patients)]
 (fn [{:keys [db]} [_ id]]
   (let [{:keys [total offset]} (:data db)
         total' (dec total)
         move-to-prev-page? (<= total' offset)
         offset' (if move-to-prev-page?
                   (max 0 (- offset items-per-page))
                   offset)
         page (calc-page offset' items-per-page)]
     {:db (-> db
              (update :data assoc
                      :total total'
                      :offset offset')
              (assoc-in [:process :request-delete-patient] :done)
              (update-in [:data :patients] (fn [v] (into [] (remove #(= (:id %) id) v)))))
      :dispatch [:patients/load-n-page-patients page]})))

(rf/reg-event-db
 :patients/switch-open-item
 [(rf/path :pages :patients)]
 (fn [db [_ id]]
   (update-in db [:ui :items id :open?] not)))

;; Subs
;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :patients/patients
 (fn [db]
   (mapv #(assoc % :fullname (->fullname %))
         (get-in db [:pages :patients :data :patients]))))

(rf/reg-sub
 :patients/pagination
 (fn [db]
   (let [{:keys [offset total]} (get-in db [:pages :patients :data])]
     {:current (calc-page offset items-per-page)
      :total (calc-page total items-per-page)})))

(rf/reg-sub
 :patients/item-open?
 (fn [db [_ id]]
   (get-in db [:pages :patients :ui :items id :open?])))

(rf/reg-sub
 :patients/item-loading?
 (fn [db [_ id]]
   (boolean (get-in db [:pages :patients :ui :items id :loading?]))))

(rf/reg-sub
 :patients/patient-subset-loading?
 (fn [db _]
   (let [patients (get-in db [:pages :patients :data :patients])
         req-status (get-in db [:pages :patients :process :request-fetch-patients])]
     (and (not (empty? patients))
          (= req-status :work)))))

(rf/reg-sub
 :patients/initial-patients-loading?
 (fn [db _]
   (let [patients (get-in db [:pages :patients :data :patients])
         req-status (get-in db [:pages :patients :process :request-fetch-patients])]
     (and (empty? patients)
          (= req-status :work)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-fx
 :submit-new-patient
 (fn [{db :db} [_ {:keys [values dirty path]}]]
   (js/console.log values path)
   {:db (fork/set-submitting db path true)}))

(rf/reg-sub
 :patient-by-id
 (fn [db [_ id]]
   (println "P:"(some #(= id (:id %))
                      (:patients db))
            (:patients db))
   (some #(= id (:id %))
         (:patients db))))
