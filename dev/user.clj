(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [figwheel.main.api :as fig]
            [patients.config]
            [patients.server]
            [patients.db]))

(defn start-server []
  (mount/start))

(defn stop-server []
  (mount/stop))

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

;; Doesn't work because of figwheel repl
(defn reset []
  (stop)
  (refresh :after 'user/start))

(comment
  (start-server)

  (start)

  (stop)

  (reset))
