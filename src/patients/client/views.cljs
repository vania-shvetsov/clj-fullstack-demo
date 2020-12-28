(ns patients.client.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [patients.client.state :as s]))

(def gender-string {"male" "Мужской"
                    "female" "Женский"})

(defn link-to [{:keys [class href]} & children]
  (apply conj
   [:a {:class class
        :on-click #(rf/dispatch [:navto href])}]
   children))

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
  [{:keys [index patient]}]
  (r/with-let [open? (rf/subscribe [:patient-item-open? (:id patient)])
               loading? (rf/subscribe [:patient-item-loading? (:id patient)])]
    (let [{:keys [id fullname fullness]} patient
          handle-switch-open (fn []
                               (rf/dispatch [:switch-open-patient-item id])
                               (when (= fullness :partial)
                                 (rf/dispatch [:fetch-patient-by-id id])))]
      [:div.box.mb-3
       [:div.level
        [:div.level-left
         [:div.has-text-weight-medium.is-size-5 fullname]]
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
              {:on-click #(rf/dispatch [:delete-patient id index])}
              [:span.icon [:i.fas.fa-trash-alt]]
              [:span "Удалить"]]]]])])))

(defn patient-list []
  (r/with-let [patients (rf/subscribe [:patients])
               request-work? (rf/subscribe [:request-work? :fetch-patients])]
    [:div.container
     (when (and (empty? @patients) @request-work?)
       [:span.icon [:i.fas.fa-spinner.fa-pulse]])
     (map-indexed (fn [i x] ^{:key (:id x)} [patient-item {:patient x
                                                           :index i}])
                  @patients)]))

(defn pagination []
  (r/with-let [pagination (rf/subscribe [:pagination])
               request-work? (rf/subscribe [:request-work? :fetch-patients])]
    (let [{:keys [total current]} @pagination]
      (when (> total 1)
        [:div.pb-5.pt-1
         [:nav.pagination.is-right
          [:a.pagination-previous.button.is-outlined
           {:disabled (= current 0)
            :on-click #(rf/dispatch [:fetch-patients (dec current)])}
           [:span.icon [:i.fas.fa-long-arrow-alt-left]]]
          [:a.pagination-next.button.is-outlined
           {:disabled (= current (dec total))
            :on-click #(rf/dispatch [:fetch-patients (inc current)])}
           [:span.icon [:i.fas.fa-long-arrow-alt-right]]]
          [:div.pagination-list
           [:div.ml-4
            (inc current) "/" total]
           (when @request-work?
             [:span.icon.ml-3 [:i.fas.fa-spinner.fa-pulse]])]]]))))

(defn page-patients []
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [pagination (rf/subscribe [:pagination])]
        (rf/dispatch [:fetch-patients (:current @pagination)])))

    :reagent-render
    (fn []
      [:div.container
       [:div.level.mt-5
        [:div.level-left
         [:div.level-item
          [link-to {:href "/new-patient"
                    :class "is-success button"}
           [:span.icon.is-small [:i.fas.fa-plus]]
           [:span "Новый пациент"]]]]]
       [:div.level [patient-list]]
       [:div.level [pagination]]])}))

(defn page-new-patient []
  [:div.panel
   [:p.panel-heading "Новый пациент"]])

(defn page-edit-patient []
  [:div.panel
   [:p.panel-heading "Редактирование пациента"]])

(defn page-not-found []
  [:div.is-size-4.has-text-weight-light "Нет такой страницы :("])

(defn app [router]
  [:div.container.is-max-desktop
   [:nav.navbar
    [:div.navbar-brand
     [link-to {:href "/"
               :class "navbar-item is-size-4 has-text-weight-medium"}
      "Мои пациенты"]]]
   [router]])
