(ns routom.core
  (:require [om.next :as om]
            [routom.util :as u]
            [goog.module.ModuleManager :as module-manager])
  (:import goog.module.ModuleManager))

(defprotocol IRootQuery
  (root-query [this] "Return the component's unbound query to be applied to the application's root component"))

(defn ^boolean irootquery? [x]
  (if (implements? IRootQuery x)
    true
    (when (goog/isFunction x)
      (let [x (js/Object.create (. x -prototype))]
        (implements? IRootQuery x)))))

(defmulti init-module (fn [module-id] module-id))

(defn- load-module!
  [module-id route-path routes-atom]
  (.execOnLoad (module-manager/getInstance) module-id
               #(let [new-route (init-module module-id)]
                 (js/setTimeout
                   (fn [] (swap! routes-atom assoc-in (u/path->keys route-path) new-route)) 1000)
                 )))

(defn- get-route-query
  [route-atom default-query path route-params]
  (loop [p path
         params {}
         query default-query]
    (if (empty? p)
      {:query  query
       :params (merge params route-params)}
      (let [keys (u/path->keys p)
            {:keys [ui module-id] :as route} (get-in @route-atom keys)]
        (when-not route
          (throw (ex-info (str "route id at path " keys " does not exist")
                          {:type :routom/invalid-route-path})))
        (if module-id
          (do (load-module! module-id path route-atom)
              (recur [] params query))
          (let [ui (or ui route)
                root-query (if (irootquery? ui) (root-query ui) [])
                new-query (into query `[{~(last keys) ~(with-meta (om/query ui) {:component ui})}])
                new-query (into new-query root-query)
                new-params (merge params (om/params ui))]
            (recur (butlast p) new-params new-query))))
      )))
