(ns routom.datascript
  (:require [datascript.core :as d]
            [clojure.walk :as w]))

(defn strip-nil-vals
  [tx-data]
  (w/postwalk (fn [x] (if (map? x) (into {} (filter (comp some? val) x)) x)) tx-data))

(defn merge! [reconciler state res query]
  "An Om-Next merge function that works with datascript.
  This function expects the app state atom to have a key :conn
  with the value being a datascript connection"
  (let [conn (:conn state)
        tx-data (mapcat #(let [tx-data (second %)]
                           (if (map? tx-data)
                             [tx-data]
                             tx-data)) res)
        tx-data (strip-nil-vals tx-data)
        _ (d/transact! conn tx-data)]
    {:keys    (into [] (remove symbol?) (keys res))
     :next    state
     :tempids (->> (filter (comp symbol? first) res)
                   (map (comp :tempids second))
                   (reduce merge {}))}))

(defn wrap-conn [p]
  "Wraps an Om-Next parser function and
  associates a datascript conn and db in env
  so they are available to read and mutate functions.
  This function expects the app state atom to have a key :conn
  with the value being a datascript connection"
  (fn parse
    ([env query]
     (parse env query nil))
    ([{state :state :as env} query target]
     (let [st @state
           conn (:conn st)
           db (d/db conn)]
       (p (assoc env :conn conn :db db) query target)))))
