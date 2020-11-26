(ns zbx-medi.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
    ;[ring.adapter.jetty :as ring-jetty]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [zbx-medi.rbmqutils :as rbmq])
  (:import (java.net SocketTimeoutException ConnectException)
           (java.text SimpleDateFormat)))


(defn- make-response
  ([body]
   (log/infof "body -> application [%s]" body)
   {:status 200
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body  (json/write-str body)})
  ([]
   {:status 200}))

(defroutes app-routes
           (GET "/" request (do
                              (log/infof (str "Home GET " (:params request)))
                              (json/write-str {:message "Page Not Available" :ok true})))
           (GET "/slack/notify" request (let [params (:params request)
                                             _ (log/infof "GET Params %s" params)
                                             resp (rbmq/process-notification params)]
                                           (make-response resp)))
           (POST "/slack/notify" request (let [_ (log/infof "POST request %s" request)
                                               body (slurp (:body request))
                                              resp (rbmq/process-notification body)]
                                          (make-response resp)))
           (route/not-found (json/write-str {:message "Page Not Found"})))

(def apimethod
  "A default configuration for a HTTP API."
  {:params    {:urlencoded false
               :keywordize true}
   :responses {:not-modified-responses true
               :absolute-redirects     true
               :content-types          true
               :default-charset        "utf-8"}})

(def app
  (wrap-defaults app-routes apimethod))

#_(def app (wrap-params app-routes))


(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3001"))]
    (do
      (rbmq/initialize-queue-interfaces)
      (log/infof "Initializing jetty port %s"port)
      ;(ring-jetty/run-jetty app {:port port})
      (run-server app {:port port}))
    (.addShutdownHook (Runtime/getRuntime) (new Thread (fn [] (do
                                                                (rbmq/shut-down)
                                                                (log/info "Service shutting down...")))))))
