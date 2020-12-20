(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [mount.core :as mount]
            [patients.config]
            [patients.server]))

(defn restart []
  (refresh))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))
