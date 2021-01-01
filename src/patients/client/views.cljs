(ns patients.client.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [fork.re-frame :as fork]
            [patients.client.state :as s]
            [patients.utils :as utils]))

(def gender-string
  {"male" "Мужской"
   "female" "Женский"})

(defn link-to [{:keys [class href]} & children]
  (apply conj
         [:a {:class class
              :on-click #(rf/dispatch [:navigation/to href])}]
         children))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Patient list
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn details-row [{:keys [caption value]}]
  [:div.columns.is-gapless.mb-3
   [:div.column.is-one-fifth.is-size-6.has-text-grey caption]
   [:div.column value]])

(defn patient-details [{:keys [patient]}]
  (let [{:keys [gender birth-date created-at address oms-number]} patient]
    [:div.mb-5
     [:div.is-italic.is-size-7.mb-4 "Зарегистрирован: " created-at]
     [details-row {:caption "Дата рождения"
                   :value birth-date}]
     [details-row {:caption "Пол"
                   :value (gender-string gender)}]
     [details-row {:caption "Адрес"
                   :value address}]
     [details-row {:caption "Полис ОМС"
                   :value oms-number}]]))

(defn patient-item
  [{:keys [patient]}]
  (r/with-let [open? (rf/subscribe [:patients/item-open? (:id patient)])
               loading? (rf/subscribe [:patients/item-loading? (:id patient)])]
    (let [{:keys [id fullname fullness]} patient
          handle-switch-open (fn []
                               (rf/dispatch [:patients/switch-open-item id])
                               (when (= fullness :partial)
                                 (rf/dispatch [:patients/load-patient-details id])))]
      [:div.box.mb-3
       [:div.level
        [:div.level-left
         [:div.has-text-weight-medium.is-size-6 fullname]]
        [:div.level-right
         (when @loading?
           [:div.level-item
            [:span.icon
             [:i.fas.fa-spinner.fa-pulse]]])
         [:div.level-item
          [:button.button.is-white
           {:on-click handle-switch-open}
           (if (and @open? (not @loading?))
             [:span.icon {:key "up"} [:i.fas.fa-chevron-up]]
             [:span.icon {:key "down"} [:i.fas.fa-chevron-down]])]]]]
       (when (and @open? (= fullness :complete))
         [:div
          [patient-details {:patient patient}]
          [:div.level
           [:div.level-left
            [:div.level-item
             [link-to {:href (str "/edit-patient/" id)
                       :class "button"}
              [:span.icon [:i.fas.fa-pencil-alt]]
              [:span "Редактировать"]]]]
           [:div.level-right
            [:button.button.is-outlined.is-danger
             {:on-click #(rf/dispatch [:patients/try-to-delete-patient id])}
             [:span.icon [:i.fas.fa-trash-alt]]
             [:span "Удалить"]]]]])])))

(defn patient-list []
  (r/with-let [patients (rf/subscribe [:patients/patients])
               initial-loading? (rf/subscribe [:patients/initial-patients-loading?])]
    [:div.container
     (when @initial-loading?
       [:span.icon [:i.fas.fa-spinner.fa-pulse]])
     (map (fn [x] ^{:key (:id x)} [patient-item {:patient x}])
          @patients)]))

(defn pagination []
  (r/with-let [pagination (rf/subscribe [:patients/pagination])
               loading? (rf/subscribe [:patients/patient-subset-loading?])]
    (let [{:keys [total current]} @pagination]
      (when (> total 1)
        [:div.pb-5.pt-1
         [:nav.pagination.is-right
          [:a.pagination-previous.button.is-outlined
           {:disabled (= current 0)
            :on-click #(rf/dispatch [:patients/load-n-page-patients (dec current)])}
           [:span.icon [:i.fas.fa-long-arrow-alt-left]]]
          [:a.pagination-next.button.is-outlined
           {:disabled (= current (dec total))
            :on-click #(rf/dispatch [:patients/load-n-page-patients (inc current)])}
           [:span.icon [:i.fas.fa-long-arrow-alt-right]]]
          [:div.pagination-list
           [:div.ml-4
            (inc current) "/" total]
           (when @loading?
             [:span.icon.ml-3 [:i.fas.fa-spinner.fa-pulse]])]]]))))

(defn page-patients []
  (r/create-class
   {:component-did-mount
    (fn []
      (let [pagination (rf/subscribe [:patients/pagination])]
        (rf/dispatch [:patients/load-initial-patients])))

    :component-will-unmount
    (fn []
      (rf/dispatch [:reset-page :patients]))

    :reagent-render
    (fn []
      [:div
       [:div.level
        [:div.level-left
         [:div.level-item
          [link-to {:href "/new-patient"
                    :class "is-success button"}
           [:span.icon.is-small [:i.fas.fa-plus]]
           [:span "Новый пациент"]]]]]
       [:div.level [patient-list]]
       [:div.level [pagination]]])}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create/Edit patient
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn field [{:keys [input label error field-name on-change path]}]
  (let [handle-change
        (fn [e]
          (when error
            (rf/dispatch [:form/reset-field-error path field-name]))
          (on-change e))
        [input-element input-props] input]
    [:div.field
     [:label.label label]
     [:div.control
      [input-element (merge input-props
                            {:name (name field-name)
                             :on-change handle-change
                             :class (utils/class-names (:class input-props)
                                                       {"is-danger" (boolean error)})})]
      (when error
        [:div.help.is-danger error])]]))

(defn patient-form [{:keys [form-id
                            values
                            server-errors
                            handle-change
                            submitting?
                            handle-submit
                            path]}]
  [:form {:on-submit handle-submit
          :id form-id}

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [field {:input [:input.input {:value (values :first-name)
                                   :auto-complete "off"}]
             :label "Имя"
             :field-name :first-name
             :error (:first-name server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [field {:input [:input.input {:value (values :middle-name)
                                   :auto-complete "off"}]
             :label "Отчество"
             :field-name :middle-name
             :error (:middle-name server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [field {:input [:input.input {:value (values :last-name)
                                   :auto-complete "off"}]
             :label "Фамилия"
             :field-name :last-name
             :error (:last-name server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.columns.is-gapless.mb-3
    [:div.column
     [:div.field
      [:label.label "Пол"]
      [:p.control
       [:label.radio
        [:input {:name "gender"
                 :type "radio"
                 :value "male"
                 :checked (= (values :gender) "male")
                 :on-change handle-change}]
        " Мужской"]
       [:label.radio
        [:input {:name "gender"
                 :type "radio"
                 :value "female"
                 :checked (= (values :gender) "female")
                 :on-change handle-change}]
        " Женский"]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-fifth
     [field {:input [:input.input {:value (values :birth-date)
                                   :type "date"}]
             :label "Дата рождения"
             :field-name :birth-date
             :error (:birth-date server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-half
     [field {:input [:textarea.textarea {:value (values :address)}]
             :label "Адрес"
             :field-name :address
             :error (:address server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [field {:input [:input.input {:value (values :oms-number)
                                   :auto-complete "off"}]
             :label "Номер полиса ОМС"
             :field-name :oms-number
             :error (:oms-number server-errors)
             :on-change handle-change
             :path path}]]]

   [:div.level.py-5
    [:div.level-left
     [:div.level-item
      [:button.button.is-success
       {:type "submit"
        :disabled submitting?}
       "Сохранить"]]
     [:div.level-item
      [:button.button {:type "button"
                       :on-click #(rf/dispatch [:navigation/to "/"])}
       "Отмена"]]
     (when submitting?
       [:div.level-item
        [:span.icon [:i.fas.fa-spinner.fa-pulse]]])]]])

(defn page-new-patient []
  [:div
   [:div.level
    [:div.is-size-5 "Регистрация нового пациента"]]
   [fork/form {:path [:form :new-patient]
               :prevent-default? true
               :clean-on-unmount? true
               :keywordize-keys true
               :on-submit #(rf/dispatch [:new-patient/submit %])
               :initial-values {:gender "male"}}
    patient-form]])

(defn page-edit-patient [{:keys [id]}]
  (r/with-let [patient (rf/subscribe [:edit-patient/patient])
               loading? (rf/subscribe [:edit-patient/patient-loading?])
               exists? (rf/subscribe [:edit-patient/patient-exists?])]
    (r/create-class
     {:component-did-mount
      (fn [] (rf/dispatch [:edit-patient/load-patient id]))

      :component-will-unmount
      (fn []
        (rf/dispatch [:reset-page :edit-patient]))

      :reagent-render
      (fn []
        [:div
         [:div.level
          [:div.is-size-5 "Редактирование данных пациента"]]
         (cond
           @loading?
           [:span.icon [:i.fas.fa-spinner.fa-pulse]]

           @exists?
           [fork/form {:path [:form :edit-patient]
                       :initial-values @patient
                       :prevent-default? true
                       :clean-on-unmount? true
                       :keywordize-keys true
                       :on-submit #(rf/dispatch [:edit-patient/submit %])}
            patient-form]

           :else
           [:div.is-size-5.has-text-weight-light.has-text-grey
            "Пациент с идентификатором "
            [:strong id]
            " не найден в системе :("])])})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Not found
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-not-found []
  [:div.is-size-4.has-text-weight-light "Нет такой страницы :("])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; App
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn app [router]
  [:div.container.is-max-desktop
   [:nav.navbar.mb-5
    [:div.navbar-brand
     [link-to {:href "/"
               :class "navbar-item is-size-4 has-text-weight-medium"}
      "Мои пациенты"]]]
   [router]])
