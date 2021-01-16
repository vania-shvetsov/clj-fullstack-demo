(ns patients.migrations
  (:require [migratus.core :as migratus]
            [patients.config :refer [load-config]]))

(defn config []
  (let [config (load-config)]
    (assoc (:migratus config) :db (:db config))))

(defn new-migration [name]
  (migratus/create (config) name))

(defn migrate
  ([]
   (migrate (config)))
  ([db-config]
   (let [cfg (config)
         cfg' (assoc cfg :db db-config)]
     (migratus/migrate cfg'))))

(defn rollback []
  (migratus/rollback (config)))

(defn pending-list []
  (println (migratus/pending-list (config))))
