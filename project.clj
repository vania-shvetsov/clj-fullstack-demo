(defproject patients "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.namespace "1.1.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.2.18.jre7"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring/ring-devel "1.8.2"]
                 [ring/ring-json "0.5.0"]
                 [ring/ring-mock "0.4.0"]
                 [bk/ring-gzip "0.3.0"]
                 [ring-logger "1.0.1"]
                 [compojure "1.6.2"]
                 [mount "0.1.16"]
                 [aero "1.1.6"]
                 [migratus "1.3.3"]
                 [honeysql "1.0.444"]
                 [com.mchange/c3p0 "0.9.5.5"]]

  :main ^:skip-aot patients.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}

  :aliases {"new-migration" ["trampoline" "run" "-m" "patients.migrations/new-migration"]
            "migrate" ["trampoline" "run" "-m" "patients.migrations/migrate"]
            "rollback" ["trampoline" "run" "-m" "patients.migrations/rollback"]
            "pending-migrations" ["trampoline" "run" "-m" "patients.migrations/pending-list"]})
