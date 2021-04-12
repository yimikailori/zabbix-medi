(ns zbx-medi.core
  (:gen-class)
  (:use [clojure.data.zip.xml])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
    ;[ring.adapter.jetty :as ring-jetty]
            [org.httpkit.server :refer [run-server]]
            [org.httpkit.client :as http]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [zbx-medi.rbmqutils :as rbmq]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as str])
  (:import (java.net SocketTimeoutException ConnectException)
           (java.text SimpleDateFormat)
           (java.io ByteArrayInputStream File)))




(def pg-url (atom nil))
(def pg-xml (atom nil))

(defn- make-response
  ([body]
   (log/infof "body -> application [%s]" body)
   {:status 200
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body  (json/write-str body)})
  ([]
   {:status 200}))




(defn callPG
  "Calls PG and return failed or success with corresponding
  status code"
  [fullUrl request as_connect_timeout]
  (try
    (let [options {:timeout (Integer/parseInt (str as_connect_timeout))             ; ms
                   :body    (slurp (File. request))
                   :headers {"Content-Type" "application/xml"}}
          {:keys [status body error] :as resp}  @(http/post fullUrl options)]
      (if error
        (do
          (let [error-msg (condp instance? error
                            ConnectException ":timeout-on-connect"
                            SocketTimeoutException ":timeout-on-read")]
            (log/errorf "Failed, exception %s | %s" error-msg body)
            {:error error-msg, :state false}))
        (do
          (log/infof "PG response %s"body)
          (let [body-parser (-> (ByteArrayInputStream. (.getBytes body)) xml/parse zip/xml-zip)
                status (xml-> body-parser :Envelope :Body :requestPrincipalInformationResponse :return :resultCode)
                res (xml-> body-parser :xmlns.http%3A%2F%2Fschemas.xmlsoap.org%2Fsoap%2Fenvelope%2F/Envelope
                           :xmlns.http%3A%2F%2Fschemas.xmlsoap.org%2Fsoap%2Fenvelope%2F/Body
                           :xmlns.http%3A%2F%2Fexternal.interfaces.ers.seamless.com%2F/requestPrincipalInformationResponse
                           :return
                           :requestedPrincipal
                           :accounts :account :balance :value text)
                #_(xml-> body-parser :Envelope :Body :requestPrincipalInformationResponse :return :requestedPrincipal
                           :accounts :account :balance :value text)
                data-map (apply str res)
                val (str/split data-map #"\.")]
            (log/infof "Parsed response=>%s" {:balance (val 0)})
            {:balance (val 0)}))))
    (catch Exception e
      (log/errorf "Failed to connect %s" e)
      {:error "connection timeout", :state false})))


(defroutes app-routes
           (GET "/" request (do
                              (log/infof (str "Home GET " (:params request)))
                              (json/write-str {:message "Page Not Available" :ok true})))
           (GET "/glopg/alert" request (let [_ (log/infof "pg-url=%s,pg-xml=%s" @pg-url @pg-xml)
                                             resp (if @pg-url
                                                    (callPG @pg-url @pg-xml 5000)
                                                    "unknown")]
                                      (make-response resp) ))
           (GET "/slack/notify" request (let [params (:params request)
                                             _ (log/infof "GET Params %s" params)
                                             resp (rbmq/process-notification params)]
                                           (make-response resp)))
           (POST "/slack/notify" request (let [_ (log/infof "POST request %s" request)
                                               body (slurp (:body request))
                                              resp (rbmq/process-notification body)]
                                          (make-response resp)))
           (GET "/jira/notify" request (let [params (:params request)
                                             _ (log/infof "GET Params %s" params)
                                             resp (rbmq/process-jira-notification params)]
                                           (make-response resp)))
           (POST "/jira/notify" request (let [_ (log/infof "POST request %s" request)
                                               body (slurp (:body request))
                                              resp (rbmq/process-jira-notification body)]
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
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3001"))
        _ (reset! pg-url (System/getenv "pgurl"))
        _ (reset! pg-xml (System/getenv "pgxml"))]
    (do
      (rbmq/initialize-queue-interfaces)
      (log/infof "Initializing jetty port %s,pg-url=%s,pg-xml=%s"port @pg-url @pg-xml)
      ;(ring-jetty/run-jetty app {:port port})
      (run-server app {:port port}))
    (.addShutdownHook (Runtime/getRuntime) (new Thread (fn [] (do
                                                                (rbmq/shut-down)
                                                                (log/info "Service shutting down...")))))))
