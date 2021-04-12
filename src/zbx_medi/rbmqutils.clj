(ns zbx-medi.rbmqutils
  (:require
   [langohr.core :as rmq]
   [langohr.basic :as lb]
   [langohr.channel :as lch]
   [langohr.queue :as lq]
   [langohr.exchange :as le]
   [langohr.consumers :as lc]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]))


(declare initialize-publisher initialize-consumer create-consumer-handler)


;;for recovery
(def zbx-details {:host     "127.0.0.1"
                  :port     (Integer/parseInt (or (System/getenv "rbmqport") "5672"))
                  :username "guest"
                  :password "guest"
                  :vhost    "/"
                  :conn            (atom nil)
                  :channel         (atom nil)
																		 ; queue info
                  :queue-name "queue-zbx"
                  :queue-exchange "xchg-zbx"
                  :queue-routing-key "zbx"})
                  
(def jira-details {:host     "127.0.0.1"
                   :port      (Integer/parseInt (or (System/getenv "rbmqport") "5672"))
                   :username "guest"
                   :password "guest"
                   :vhost    "/"
                   :conn            (atom nil)
                   :channel         (atom nil)
																		 ; queue info
                   :queue-name "queue-jira"
                   :queue-exchange "xchg-jira"
                   :queue-routing-key "jira"})

(def queues [zbx-details jira-details])

(def ^:dynamic value 1)


;;
(defn get-rabbitmq
  [args]
  (try
    (let [{:keys [queue-name queue-exchange queue-routing-key msg channel]} args
          _ (le/declare @channel queue-exchange "direct" {:durable true :auto-delete false})
          queue-name (:queue (lq/declare @channel queue-name {:durable     true
                                                              :auto-delete false}))
					;load (json/read-str msg)
          ]
			;"{\"channel\":\"G01AWAAA6JY\",\"as_user\":\"true\",\"thread_ts\":0,\"attachments\":[{\"fallback\":\"UPDATE: {EVENT.NAME}\",\"title\":\"UPDATE: {EVENT.NAME}\",\"color\":\"#FFC859\",\"title_link\":\"http://127.0.0.1/tr_events.php?triggerid=12099&eventid=1242432\",\"pretext\":\"{EVENT.UPDATE.MESSAGE}\",\"fields\":[{\"title\":\"Host\",\"value\":\"{HOST.NAME} [{HOST.IP}]\",\"short\":true},{\"title\":\"Event time\",\"value\":\"{EVENT.UPDATE.DATE} {EVENT.UPDATE.TIME}\",\"short\":true},{\"title\":\"Severity\",\"value\":\"{EVENT.SEVERITY}\",\"short\":true},{\"title\":\"Opdata\",\"value\":\"{EVENT.OPDATA}\",\"short\":true}]}]}"
			; bind queue to exchange

      (log/infof "parse load %s" msg)
      (lq/bind @channel queue-name queue-exchange {:routing-key queue-routing-key})

      (lb/publish @channel queue-exchange queue-routing-key
                  msg
                  {"Content-type" "text/json"})
			;(rmq/close channel)
			;(rmq/close rabbitmq-conn)
      {:status true :message "success" :ok true})
    (catch Exception e
      (log/error e (.getMessage e))
      {:status false :message (str "Oops! Something bad happened, error generated [" (.getMessage e) "]") :ok true})))
;;

;[consumer] Received a message: {"msisdn":8156545907,"id":15981342110806992,"message":"Dear Customer, you have been credited with N43 airtime at N7 service charge. Please recharge by 2020-08-25 22:10:17 to repay your advance.","flash?":false,"from":"Borrow Me"}, delivery tag: 2, content type: null




(defn process-notification [args]
  (log/infof "sendZbxNotification(%s)" args)
  (get-rabbitmq (assoc zbx-details :msg args)))

(defn process-jira-notification [args]
  (log/infof "sendJiraNotification(%s)" args)
  (get-rabbitmq (assoc jira-details :msg args)))
;; Initialize Interfaces


(defn shut-down []
  (log/infof "Shutting down Rabbitmq Connections")
  (doseq [queue queues]
    (when-let [conn (:conn queue)]
      (rmq/close @conn)
      (reset! conn nil))))

(defn initialize-queue [queue]
  (try
    (let [{:keys [queue-exchange queue-name queue-routing-key handler consumers conn channel]
           :or {consumers 10}} queue
          _ (reset! conn (rmq/connect queue))
          _ (reset! channel (lch/open @conn))
          _ (le/declare @channel queue-exchange "direct" {:durable true :auto-delete false})
          queue-name (:queue (lq/declare @channel queue-name {:durable true
                                                              :auto-delete false}))]
			; bind queue to exchange
      (lq/bind @channel queue-name queue-exchange {:routing-key queue-routing-key})
			;(lq/declare @channel queue-name {:durable true :auto-delete false})
      (when handler
        (doseq [count (repeat consumers "x")]
					;(lc/subscribe channel queue-name handler)
          (lc/subscribe @channel queue-name handler {:auto-ack false}))))
    (catch Exception e (log/error e (.getMessage e))
           (throw (Exception. (format "unableToConnectToRabbitmq[%s] -> %s" queue (.getMessage e)))))))

(defn initialize-queue-interfaces []
  (doseq [queue queues]
    (initialize-queue queue))
  queues)
