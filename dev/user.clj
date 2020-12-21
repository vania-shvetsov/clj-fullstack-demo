(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [patients.config]
            [patients.server]
            [patients.db]))

(defn refresh-all []
  (refresh))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(comment
  (start)
  (stop)
  (refresh-all)
  )
