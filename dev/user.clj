(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [figwheel.main.api :as fig]
            [patients.config :refer [load-config]]
            [patients.core :refer [start-service]]
            [patients.migrations :as m]))

(defonce stop-service (atom nil))

(defn start-server []
  (when @stop-service
    (@stop-service))
  (reset! stop-service (start-service (load-config))))

(defn stop-server []
  (when @stop-service
    (@stop-service)
    (reset! stop-service nil)))

(defn start-client []
  (fig/start "dev"))

(defn stop-client []
  (fig/stop "dev"))

(defn start []
  (start-client)
  (start-server))

(defn stop []
  (stop-client)
  (stop-server))

;; TODO: Doesn't work because of figwheel repl
(defn reset []
  (stop)
  (refresh :after 'user/start))

(comment
  (start-server)
  (stop-server)
  (start-client)
  (stop-client)
  (start)
  (stop)
  (reset)
  (m/migrate))
