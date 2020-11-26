(defproject zbx-medi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :omit-source true
  :main ^:skip-aot zbx-medi.core
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [clj-http "3.10.0"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.clojure/data.json "0.2.6"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.postgresql/postgresql "42.2.5.jre7"]
                 [clj-time "0.11.0"]

                 [http-kit "2.3.0"]
                 [com.novemberain/langohr "5.1.0"]
                 [seancorfield/next.jdbc "1.1.613"]
                 [com.zaxxer/HikariCP "3.3.1"]
                 [com.stuartsierra/component "1.0.0"]
                 ]
  :plugins [[lein-ring "0.12.5"]
            [compojure "1.6.1"]]
  :ring {:handler zbx-medi.core/app}
  :profiles {:uberjar {:omit-source    true
                       :aot            :all
                       :uberjar-name   "zbx-medi.jar"
                       :source-paths   ["env/prod/clj"]
                       :resource-paths ["env/prod/resources"]
                       }
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}})
