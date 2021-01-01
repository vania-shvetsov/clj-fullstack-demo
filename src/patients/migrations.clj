(ns patients.migrations
  (:require [mount.core :as mount]
            [migratus.core :as migratus]
            [patients.config :as c]))

(defn config []
  (mount/start #'patients.config/config)
  (assoc (:migratus c/config) :db (:db c/config)))

(defn new-migration [name]
  (migratus/create (config) name))

(defn migrate []
  (migratus/migrate (config)))

(defn rollback []
  (migratus/rollback (config)))

(defn pending-list []
  (println (migratus/pending-list (config))))
