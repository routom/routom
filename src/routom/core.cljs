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

(defn- -load-module!
  [module-id route-path routes-atom]
  (.execOnLoad (module-manager/getInstance) module-id
               #(let [new-route (init-module module-id)]
                 (swap! routes-atom assoc-in (u/path->keys route-path) new-route)
                 )))

(def load-module! (memoize -load-module!))

(defn- get-route-query
  [route-atom default-query path route-params]
  (loop [p path
         params {}
         query default-query]
    (if (empty? p)
      {:query  (if (empty? query) nil query)
       :params (merge params route-params)}
      (let [keys (u/path->keys p)
            {:keys [ui module-id] :as route} (get-in @route-atom keys)]
        (when-not route
          (throw (ex-info (str "route id at path " keys " does not exist")
                          {:type :routom/invalid-route-path})))
        (if module-id
          (do (load-module! module-id path route-atom)
              (recur [] params query))
          (if ui
            (let [root-query (if (irootquery? ui) (root-query ui) [])
                  new-query (into query `[{~(last keys) ~(with-meta (om/query ui) {:component ui})}])
                  new-query (into new-query root-query)
                  new-params (merge params (om/params ui))]
              (recur (butlast p) new-params new-query))
            (recur (butlast p) params query))))
      )))

(defn module-status
  [render-status]
  (ui
    Object
    (initLocalState [_]
                    {:status :loading})
    (componentWillMount
      [this]
      (let [{:keys [module-id]} (om/props this)
            manager (module-manager/getInstance)]
        (if-let [module (.getModuleInfo manager module-id)]
          (if (.-isLoaded module)
            (om/update-state! this assoc :status :success)
            (let [on-error (.registerErrback module (fn [_] (om/update-state! this assoc :status :error)))
                  on-load (.registerCallback module (fn [_] (om/update-state! this assoc :status :success)))]
              (om/update-state! this assoc :on-err on-error)
              (om/update-state! this assoc :on-load on-load))))))
    (componentWillUnmount [this]
                          (let [{:keys [on-error on-load]} (om/get-state this)]
                            (when on-error (.abort on-error))
                            (when on-load (.abort on-load))))
    (render [this]
            (let [{:keys [module-id]} (om/props this)
                  {:keys [status]} (om/get-state this)]
              (when render-status (render-status status module-id))))))

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
                 (when render-module-status
                   #((om/factory render-module-status {:keyfn :module-id})
                     {:module-id module-id})))
          (if ui
            (let [route-id (last p)
                  fac #(om/factory ui {:keyfn (fn [_] route-id)})
                  props (or (get root-props route-id) {})
                  props (vary-meta props assoc :om-path [route-id])
                  child-element (if element
                                  #((fac) (om/computed props (merge root-props %1)) element)
                                  #((fac) (om/computed props (merge root-props %1)))
                                  )]
              (recur (butlast p) child-element))
            (recur (butlast p) element)))))))

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
  (let [new-query (get-active-query route-atom hierarchy-atom route-id route-params)]
    (om/set-query! component new-query)
    ))


(defn init-router
  ([routes]
   (init-router routes nil))
  ([routes render-module-status]

   (let [route-hierarchy (atom (u/create-hierarchy @routes))
         active-route (atom nil)]
     (add-watch routes :route/hierarchy (fn [_ _ _ next-state]
                                          (reset! route-hierarchy (u/create-hierarchy next-state))))
     (let [set-active-route! #(reset! active-route %)
           ModuleStatus (when render-module-status
                          (module-status render-module-status))
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
                     (let [{:keys [route/id] :as route} @active-route]
                       (if route
                         (let [path (u/get-path @route-hierarchy id)
                               props (om/props this)
                               root-props (merge
                                            props
                                            route)
                               element-tree (get-element-tree @routes path root-props ModuleStatus)]
                           (element-tree)))
                       )))]
       {:root-class AppRoot
        :set-route! set-active-route!
        :ui->props  (fn [env c]
                      (let [props (om/default-ui->props env c)]
                        (when props (merge props @active-route))))
        :get-route  (fn [] @active-route)}))))

(defn has-subroute
  [component]
  (if (om/children component)
    true
    false))

(defn try-render-subroute
  ([component]
   (try-render-subroute component {}))
  ([component more-computed]
   (let [subroutef (first (om/children component))]
     (when subroutef (subroutef more-computed)))))
(defn render-subroute
  ([component]
    (render-subroute component {}))
  ([component more-computed]
    (let [subroutef (first (om/children component))]
      (subroutef more-computed))))







