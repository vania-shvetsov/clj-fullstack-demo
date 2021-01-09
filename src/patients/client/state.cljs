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
          :response-format default-response-format
          :headers {"Content-Type" "application/json"}}))

(defn ->fullname [{:keys [first-name middle-name last-name]}]
  (str (string/capitalize first-name) " "
       (string/capitalize middle-name) " "
       (string/capitalize last-name)))

(defn calc-page-number [items-count per-page]
  (.ceil js/Math (/ items-count per-page)))

(defn persist-server-validation-result [db response path]
  (if (= (:error response) "invalid_data")
    (reduce (fn [db' [k msg]] (fork/set-error db' path k msg))
            db
            (:data response))
    (fork/set-server-message db path "Неизвестная ошибка формы")))

(def items-per-page 4)

(def default-page-states
  {:patients {:data {:offset 0
                     :total 0
                     :patients []}
              :ui {:items {}}
              :process {:request-fetch-patients :init
                        :request-fetch-patient-by-id :init
                        :request-delete-patient :init}}

   :edit-patient {:data {:patient nil}
                  :process {:request-fetch-patient :init}}})

(def initial-db
  {:route nil
   :pages {:patients (:patients default-page-states)
           :edit-patient (:edit-patient default-page-states)}})

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

(rf/reg-event-db
 :form/reset-field-error
 (fn [db [_ path field-name]]
   (update-in db (conj path :server) dissoc field-name)))

(rf/reg-event-db
 :reset-page
 (fn [db [_ page-name]]
   (assoc-in db [:pages page-name] (get default-page-states page-name))))

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
 (fn [_ _] initial-db))

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
 (fn [{db :db} [_ page]]
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
 (fn [{db :db} [_ id]]
   {:db (-> db
            (assoc-in [:process :request-fetch-patient-by-id] :work)
            (assoc-in [:ui :items id :loading?] true))
    :http-xhrio (in-json {:method :get
                          :uri (str "/api/patients/" id)
                          :on-success [:patients/_load-patient-details-ok]
                          :on-failure [:patients/_load-patient-details-err id]})}))

(rf/reg-event-db
 :patients/_load-patient-details-ok
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
 :patients/_load-patient-details-err
 [(rf/path :pages :patients)]
 (fn [db [_ id]]
   (-> db
       (assoc-in [:process :request-fetch-patient-by-id] :error)
       (assoc-in [:ui :items id :loading?] false))))

(rf/reg-event-fx
 :patients/try-to-delete-patient
 (fn [_ [_ id]]
   {:dialog/confirm {:message "Удалить данные о пациенте?"
                     :on-ok [:patients/_actual-delete-patient id]}}))

(rf/reg-event-fx
 :patients/_actual-delete-patient
 [(rf/path :pages :patients)]
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:process :request-delete-patient] :work)
    :http-xhrio (in-json {:method :delete
                          :uri (str "/api/patients/" id)
                          :on-success [:patients/_delete-patient-ok id]})}))

(rf/reg-event-fx
 :patients/_delete-patient-ok
 [(rf/path :pages :patients)]
 (fn [{db :db} [_ id]]
   (let [{:keys [total offset]} (:data db)
         total' (dec total)
         move-to-prev-page? (<= total' offset)
         offset' (if move-to-prev-page?
                   (max 0 (- offset items-per-page))
                   offset)
         page (calc-page-number offset' items-per-page)]
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
 :patients/_page
 (fn [db]
   (get-in db [:pages :patients])))

(rf/reg-sub
 :patients/patients
 :<- [:patients/_page]
 (fn [page _]
   (mapv #(assoc % :fullname (->fullname %))
         (get-in page [:data :patients]))))

(rf/reg-sub
 :patients/pagination
 :<- [:patients/_page]
 (fn [page]
   (let [{:keys [offset total]} (:data page)]
     {:current (calc-page-number offset items-per-page)
      :total (calc-page-number total items-per-page)})))

(rf/reg-sub
 :patients/item-open?
 :<- [:patients/_page]
 (fn [page [_ id]]
   (boolean (get-in page [:ui :items id :open?]))))

(rf/reg-sub
 :patients/item-loading?
 :<- [:patients/_page]
 (fn [page [_ id]]
   (boolean (get-in page [:ui :items id :loading?]))))

(rf/reg-sub
 :patients/patient-subset-loading?
 :<- [:patients/_page]
 (fn [page _]
   (let [patients (get-in page [:data :patients])
         req-status (get-in page [:process :request-fetch-patients])]
     (boolean (and (seq patients)
                   (= req-status :work))))))

(rf/reg-sub
 :patients/initial-patients-loading?
 :<- [:patients/_page]
 (fn [page _]
   (let [patients (get-in page [:data :patients])
         req-status (get-in page [:process :request-fetch-patients])]
     (boolean (and (empty? patients)
                   (= req-status :work))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page "Edit patient"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Events
;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-fx
 :edit-patient/submit
 (fn [{db :db} [_ {:keys [values path]}]]
   {:db (fork/set-submitting db path true)
    :http-xhrio (in-json {:method :put
                          :params (dissoc values :id :created-at)
                          :uri (str "/api/patients/" (:id values))
                          :on-success [:edit-patient/_save-patient-ok path]
                          :on-failure [:edit-patient/_save-patient-err path]})}))

(rf/reg-event-fx
 :edit-patient/_save-patient-ok
 (fn [{db :db} [_ path]]
   {:db (fork/set-submitting db path false)
    :dispatch [:navigation/to "/"]}))

(rf/reg-event-db
 :edit-patient/_save-patient-err
 (fn [db [_ path {:keys [response]}]]
   (-> db
       (persist-server-validation-result response path)
       (fork/set-submitting path false))))

(rf/reg-event-fx
 :edit-patient/load-patient
 [(rf/path :pages :edit-patient)]
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:process :request-fetch-patient] :work)
    :http-xhrio (in-json {:method :get
                          :uri (str "/api/patients/" id)
                          :on-success [:edit-patient/_fetch-patient-ok]
                          :on-failure [:edit-patient/_fetch-patient-err]})}))

(rf/reg-event-db
 :edit-patient/_fetch-patient-ok
 [(rf/path :pages :edit-patient)]
 (fn [db [_ response]]
   (let [{:keys [data]} response]
     (-> db
         (assoc-in [:process :request-fetch-patient] :done)
         (assoc-in [:data :patient] data)))))

(rf/reg-event-db
 :edit-patient/_fetch-patient-err
 [(rf/path :pages :edit-patient)]
 (fn [db]
   (assoc-in db [:process :request-fetch-patient] :error)))

;; Subs
;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :edit-patient/_page
 (fn [db]
   (get-in db [:pages :edit-patient])))

(rf/reg-sub
 :edit-patient/patient
 :<- [:edit-patient/_page]
 (fn [page _]
   (get-in page [:data :patient])))

(rf/reg-sub
 :edit-patient/patient-loading?
 :<- [:edit-patient/_page]
 (fn [page _]
   (let [req-status (get-in page [:process :request-fetch-patient])]
     (boolean (#{:work :init} req-status)))))

(rf/reg-sub
 :edit-patient/patient-exists?
 :<- [:edit-patient/_page]
 (fn [page _]
   (let [req-status (get-in page [:process :request-fetch-patient])
         patient (get-in page [:data :patient])]
     (boolean (and (#{:done :error} req-status)
                   (some? patient))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page "New patient"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-fx
 :new-patient/submit
 (fn [{db :db} [_ {:keys [values path]}]]
   {:db (fork/set-submitting db path true)
    :http-xhrio (in-json {:method :post
                          :params values
                          :uri "/api/patients"
                          :on-success [:new-patient/_create-patient-ok path]
                          :on-failure [:new-patient/_create-patient-err path]})}))

(rf/reg-event-fx
 :new-patient/_create-patient-ok
 (fn [{db :db} [_ path]]
   {:db (fork/set-submitting db path false)
    :dispatch [:navigation/to "/"]}))

(rf/reg-event-db
 :new-patient/_create-patient-err
 (fn [db [_ path {:keys [response]}]]
   (-> db
       (persist-server-validation-result response path)
       (fork/set-submitting path false))))
