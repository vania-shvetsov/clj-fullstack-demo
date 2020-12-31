(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [figwheel.main.api :as fig]
            [patients.config]
            [patients.server]
            [patients.db]))

(defn start-client []
  (fig/start "dev"))

(defn stop-client []
  (fig/stop "dev"))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(defn reset []
  (stop)
  (refresh :after 'user/start))

(comment
  (start)
  (stop)
  (reset)
  (start-client)
  (stop-client))
