(ns ^{:doc
      "Core namespace, stuff that is picked up by 'lein ring server"}
    eplk.core
  (:require [eplk.driver :as driver]
            [eplk.events :as events]
            [eplk.utils :as utils])
  (:require [ring.adapter.jetty :refer [run-jetty]])
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.core.async :as a :refer [go chan >!! <!!  timeout]])
  (:gen-class))

(s/defschema MachineCycled
  {:machine_id s/Str
   :timestamp Long
   })

(s/defschema MachineCycledResponse
  {:machine_id s/Str
   :timestamp s/Str
   :type s/Str
   })

;; this is where the events channel is maintained during the interactive session
(defonce in-ch (delay (chan 10)))

(defn machine-start [& {:keys [channel]
                        :or {channel @in-ch}}]
  "Start the machine consuming the events in the channel (on a thread-pool)"
  (driver/run-with-chan channel))


(defn submit-event [machine-id]
  "Performs a blocking put of the event onto the interactive events channel"
  (utils/submit-event @in-ch machine-id))

(def app
  (api ;; macro that builds the whole REST API, which is what you see on port 3000, fully equipped with a web-client to make the api requests.
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Simple"
                   :description "Compojure Api example"}
            :tags [{:name "api", :description "Machine Cycles API"}]}}}
   (context "/api" []
            :tags ["api"]
            (POST "/event" []
                  :return MachineCycledResponse
                  :body [event MachineCycled]
                  :description "value of a :description key inside api's context goes here"
                  :summary "processes a MachineCycled event"
                  (let [mid (:machine_id event)]
                    (println "Submitting event for" mid "through the REST API endpoint")
                    (ok (submit-event mid)))))))

(defn -main [& args]
  (println "eplk.core./-main starting the machine")
  (machine-start :channel @in-ch)
  (println "now launching the web app")
  (run-jetty app {:port (Integer/valueOf (or (System/getenv "port") "3000"))}))
