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
                 [aero "1.1.6"]
                 [migratus "1.3.3"]
                 [honeysql "1.0.444"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 [clj-time "0.15.2"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/clojurescript "1.10.773"]
                 [reagent "1.0.0"]
                 [cljs-ajax "0.8.1"]
                 [re-frame "1.1.2"]
                 [day8.re-frame/http-fx "0.2.2"]
                 [clj-commons/secretary "1.2.4"]
                 [fork "2.2.5"]
                 [com.bhauman/figwheel-main "0.2.12"]
                 [cider/piggieback "0.5.2"]
                 [re-frisk "1.3.5"]]

  :main ^:skip-aot patients.core

  :target-path "target/%s"

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-cljfmt "0.7.0"]]

  :uberjar-name "patients-standalone.jar"

  :profiles {:uberjar {:aot :all
                       :main patients.core}

             :dev {:source-paths ["src" "dev"]}}

  :test-selectors {:integration :integration
                   :default (complement :integration)}

  :cljsbuild
  {:builds [{:source-paths ["src"]
             :compiler {:main "patients.client.core"
                        :output-to "resources/public/cljs-out/dev-main.js"
                        :optimizations :advanced
                        :pretty-print false}}]}

  :aliases {"new-migration" ["trampoline" "run" "-m" "patients.migrations/new-migration"]
            "migrate" ["trampoline" "run" "-m" "patients.migrations/migrate"]
            "rollback" ["trampoline" "run" "-m" "patients.migrations/rollback"]
            "pending-migrations" ["trampoline" "run" "-m" "patients.migrations/pending-list"]})
