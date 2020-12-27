(ns patients.client.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [patients.client.state :as s]))

(defn link-to [{:keys [class href]} & children]
  [:a {:class class
       :on-click #(rf/dispatch [:navto href])}
   children])

(defn patient-item
  [{:keys [patient]}]
  (r/with-let [open? (rf/subscribe [:patient-item-open? (:id patient)])]
    (let [{:keys [id fullname birth-date]} patient]
      (println (if @open? "fa-chevron-up" "fa-chevron-down"))
      [:div.box.mb-3
       [:div.level
        [:div.level-left
         [:div fullname]
         [:div.ml-4.has-text-grey birth-date]]
        [:div.level-right
         [:div.level-item
          [:button.button.is-white
           {:on-click #(rf/dispatch [:switch-open-patient-item id])}
           (if @open?
             [:span.icon {:key "up"} [:i.fas.fa-chevron-up]]
             [:span.icon {:key "down"} [:i.fas.fa-chevron-down]])]]]]
       (when @open?
         [:div "Details"])])))

(defn patient-list []
  (r/with-let [patients (rf/subscribe [:patients])]
    [:div.container
     (map (fn [x] ^{:key (:id x)} [patient-item {:patient x}])
          @patients)]))

(defn pagination []
  (r/with-let [pagination (rf/subscribe [:pagination])]
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
            (inc current) "/" total]]]]))))

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
                    :class "is-light button is-link"}
           "Новый пациент"]]]]
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
