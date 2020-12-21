(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [patients.config]
            [patients.server]
            [patients.db]))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(defn refresh-all []
  (stop)
  (refresh :after 'user/start))

(comment
  (start)
  (stop)
  (refresh-all)
  )
