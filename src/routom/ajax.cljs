(ns routom.ajax
  (:require [goog.net.EventType :as EventType]
            [goog.events :as events])
  (:import [goog.net XhrManager]
           [goog.structs Map]))

(defn map->XhrManager [{:keys [max-retries headers min-count max-count timeout-interval with-credentials]}]
  (XhrManager. max-retries (when headers (Map. (clj->js headers))) headers min-count max-count timeout-interval with-credentials))

(defn event->clj [e]
  "Creats a persistent map from a goog.net.XhrManager.Event object"
  {:xhrIo  (.-xhrIo e)
   :type   (.-type e)
   :id     (.-id e)
   :target (.-target e)})

(defn- listen
  [src event-type handler]
  (events/listen
    src
    event-type
    handler))

(defn- unlisten
  [keys]
  (when keys
    (doseq [key keys]
      (events/unlistenByKey key))))

(defn send
  "Sends an ajax request using goog.net.XhrManager"
  ([xhrm {:keys [request-id url handlers
                 method content headers priority
                 callback max-retries response-type with-credentials]
          :or   {request-id (str (random-uuid))}}]
   (let [handler-keys (atom nil)
         complete-keys (atom nil)]
     (letfn [(wrap [handler]
               (fn [e]
                 (when (= (.-id e) request-id)
                   (handler (event->clj e)))))
             (on-complete [_]
               (unlisten @handler-keys)
               (unlisten @complete-keys))]
       (reset! handler-keys (mapv (fn [[event-type handler]]
                                    (events/listen xhrm event-type (wrap handler))) handlers))
       (reset! complete-keys (mapv #(events/listen xhrm % (wrap on-complete))
                                   [EventType/ABORT EventType/ERROR EventType/SUCCESS])))
     (.send xhrm
            request-id
            url
            method
            content
            headers
            priority
            callback
            max-retries
            response-type
            with-credentials)
     #(.abort xhrm request-id))))

