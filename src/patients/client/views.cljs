(ns patients.client.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [fork.re-frame :as fork]
            [patients.client.state :as s]))

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
    (fn [this]
      (let [pagination (rf/subscribe [:patients/pagination])]
        (rf/dispatch [:patients/load-initial-patients])))

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

(defn patient-form [{:keys [values
                            handle-change
                            handle-blur
                            submitting?
                            handle-submit]}]
  (js/console.log values)
  [:form {:on-submit handle-submit}

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [:div.field
      [:label.label "Имя"]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:name "first-name"
                        :value (values :first-name)
                        :on-change handle-change}]]]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [:div.field
      [:label.label "Отчество"]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:name "middle-name"
                        :value (values :middle-name)
                        :on-change handle-change}]]]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [:div.field
      [:label.label "Фамилия"]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:name "last-name"
                        :value (values :last-name)
                        :on-change handle-change}]]]]]]]

   [:div.columns.is-gapless.mb-3
    [:div.column
     [:div.field
      [:label.label "Пол"]
      [:div.field-body
       [:div.field
        [:p.control
         [:label.radio
          [:input {:name "gender"
                   :type "radio"
                   :value "male"
                   :on-change handle-change}]
          " Мужской"]
         [:label.radio
          [:input {:name "gender"
                   :type "radio"
                   :value "female"
                   :on-change handle-change}]
          " Женский"]]]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-fifth
     [:div.field
      [:label.label "Дата рождения"]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:name "birth-date"
                        :type "date"
                        :value (values :birth-date)
                        :on-change handle-change}]]]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-half
     [:div.field
      [:label.label "Адрес"]
      [:div.field-body
       [:div.field
        [:p.control
         [:textarea.textarea {:name "address"
                              :value (values :address)
                              :on-change handle-change}]]]]]]]

   [:div.columns.is-gapless.mb-2
    [:div.column.is-one-third
     [:div.field
      [:label.label "Номер полиса ОМС"]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:name "oms-number"
                        :value (values :oms-number)
                        :on-change handle-change}]]]]]]]

   [:div.level.py-5
    [:div.level-left
     [:div.level-item
      [:button.button.is-success
       {:type "submit"
        :disabled submitting?}
       "Сохранить"]]
     [:div.level-item
      [:button.button {:on-click #(rf/dispatch [:navigation/back])}
       "Отмена"]]]]])

(defn page-new-patient []
  [:div
   [:div.level
    [:div.is-size-5 "Регистрация нового пациента"]]
   [fork/form {:path [:new-patient]
               :prevent-default? true
               :clean-on-unmount? true
               :keywordize-keys true
               :on-submit #(rf/dispatch [:submit-new-patient %])}
    patient-form]])

(defn page-edit-patient [{:keys [id]}]
  (r/with-let [patient (rf/subscribe [:patient-by-id id])]
    (println @patient)
    [:div
     [:div.level
      [:div.is-size-5 "Редактирование данных пациента"]]]))

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
