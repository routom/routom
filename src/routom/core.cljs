(ns routom.core
  (:require [om.next :as om :refer-macros [ui defui]]
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

(defui ModuleStatus
  Object
  (initLocalState [_]
    {:status :loading})
  (componentWillMount
    [this]
    (let [{:keys [module-id]} (om/props this)
          manager (module-manager/getInstance)]
      (if-let [module (.getModuleInfo manager module-id)]
        (let [on-error (.registerErrback module (fn [_] (om/update-state! this assoc :status :error)))
              on-load (.registerCallback module (fn [_] (om/update-state! this assoc :status :success)))]
          (om/update-state! this assoc :on-err on-error)
          (om/update-state! this assoc :on-load on-load)))))
  (componentWillUnmount [this]
    (let [{:keys [on-error on-load]} (om/get-state this)]
      (when on-error (.abort on-error))
      (when on-load (.abort on-load))))
  (render [this]
    (let [{:keys [render-status module-id]} (om/props this)
          {:keys [status]} (om/get-state this)]
      (when render-status (render-status status module-id)))))

(defn get-element-tree
  [route-map path root-props render-module-status]
  (loop [p path
         element nil]
    (if (empty? p)
      element
      (let [keys (u/path->keys p)
            {:keys [ui module-id] :as route} (get-in route-map keys)]

        (if module-id
          (recur (butlast p)
                 (when (some? render-module-status)
                   ((om/factory ModuleStatus)
                     {:module-id     module-id
                      :render-status render-module-status})))
          (let [fac (if ui #(om/factory ui) #(om/factory route))
                props (get root-props (last p))
                child-element (if element
                                ((fac) (om/computed props root-props) element)
                                ((fac) (om/computed props root-props))
                                )]
            (recur (butlast p) child-element)))))))

(defn- get-active-query
  [route-atom hierarchy-atom route-id route-params]
  (let [routes @route-atom
        hierarchy @hierarchy-atom
        path (u/get-path hierarchy route-id)
        route (u/get-route routes path)]
    (if (some? route)
      (get-route-query route-atom [] path route-params))))

(defn- set-active-query!
  [component route-atom hierarchy-atom route-id route-params]
  (om/set-query! component (get-active-query route-atom hierarchy-atom route-id route-params)))


(defn init-router
  ([routes]
   (init-router routes nil))
  ([routes render-module-status]

   (let [route-hierarchy (atom (u/create-hierarchy @routes))
         active-route (atom nil)]
     (add-watch routes :route/hierarchy (fn [_ _ _ next-state]
                                          (reset! route-hierarchy (u/create-hierarchy next-state))))
     (let [set-active-route! #(reset! active-route %1)
           AppRoot
           (ui
             static om/IQueryParams
             (params
               [this]
               (when-let [{:keys [route/id route/params]} @active-route]
                 (:params (get-active-query routes route-hierarchy id params))))
             static om/IQuery
             (query
               [this]
               (when-let [{:keys [route/id route/params]} @active-route]
                 (:query (get-active-query routes route-hierarchy id params))))
             Object
             (componentWillMount
               [this]

               (letfn [(on-route-changed [{:keys [route/id route/params] :as active-route}]

                         (when active-route
                           (set-active-query! this routes route-hierarchy id params)))]
                 (add-watch
                   active-route
                   :app-root
                   (fn [_ _ _ next]
                     (on-route-changed next)))
                 (on-route-changed @active-route)
                 ))
             (componentWillUnmount
               [this]
               (remove-watch active-route :app-root))
             (render [this]
                     (let [{:keys [route/id] :as active-route} @active-route]
                       (if active-route
                         (let [path (u/get-path @route-hierarchy id)
                               props (om/props this)
                               root-props (merge
                                            props
                                            active-route)
                               element-tree (get-element-tree @routes path root-props render-module-status)]
                           element-tree))
                       )))]
       {:root-class           AppRoot
        :set-route! set-active-route!}))))







