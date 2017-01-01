(ns routom.remoting
  (:require [cljs.core.async :as async]))

(defmulti start-send-loop (fn [target opts] target))

(defmulti send (fn [target query callback] target))

(defmethod send :default
  [target query callback]
  (callback query))

(defn create-send-fn
  "takes a map where the key is a remote target and the value
  is an async channel on which results from the remote operation will be put.
  Returns a function that can be used as the :send function for an om.next's reconciler"
  [target-chan-map]
  (fn [target-query-map callback]
    (doseq [[target query] target-query-map]
      (send target query #(async/put! (get target-chan-map target) [% callback])))))