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

(defn call-proto
  [x pred f]
  (if (pred x)
    (f x)
    ;; in advanced, statics will get elided
    (when (goog/isFunction x)
      (let [y (js/Object.create (. x -prototype))]
        (when (pred y)
          (f y))))))

(defn- get-route-query
  [route-atom default-query path route-params]
  (loop [p path
         params {}
         query default-query
         component-query nil]
    (if (empty? p)
      {:query  query #_ (conj query component-query)
       :params (merge params route-params)}
      (let [keys (u/path->keys p)
            {:keys [ui module-id] :as route} (get-in @route-atom keys)]
        (when-not route
          (throw (ex-info (str "route id at path " keys " does not exist")
                          {:type :routom/invalid-route-path})))
        (if module-id
          (do (load-module! module-id path route-atom)
              (recur (butlast p) params query component-query))
          (if ui
            (let [root-query (or (call-proto ui #(implements? IRootQuery %) root-query) [])
                  query-expr (with-meta (call-proto ui #(implements? om/IQuery %) om/query) {:component ui})
                  component-query (if component-query (conj query-expr component-query) query-expr)
                  component-query {(last keys) query-expr}
                  new-query (into query root-query)
                  new-query (conj new-query component-query)
                  new-params (merge params (call-proto ui #(implements? om/IQuery %) om/params))]
              (recur (butlast p) new-params new-query component-query))
            (recur (butlast p) params query component-query))))
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
  (loop [component-path [(first path)]
         route-path component-path
         rst (rest path)
         add-to-parent nil]
    (if (nil? route-path)
      (add-to-parent nil)
      (let [[nxt & rst*] rst
            route-path* (if nxt (conj route-path nxt) nil)]
        (let [keys (u/path->keys route-path)
              {:keys [ui module-id] :as route} (get-in route-map keys)]

          (if module-id
            (let [fac #(om/factory render-module-status {:keyfn :module-id})
                  props (vary-meta {:module-id module-id} :om-path component-path)
                  [_ fac merge-props :as tuple] [render-module-status fac (fn [_] props)]]
              (when render-module-status
                (recur component-path
                       route-path*
                       rst*
                       #(add-to-parent ((fac) (merge-props {}) %)))
                ))
            (if ui
              (let [route-id (last component-path)
                    fac #(om/factory ui {:keyfn (fn [_] route-id)})
                    props (or (get-in root-props component-path) {})
                    props (vary-meta props assoc :om-path component-path)
                    [_ fac merge-props :as tuple] [ui fac #(om/computed props (merge root-props %1))]
                    component-path* [nxt] #_ (if nxt (conj component-path nxt))

                    ]
                (if add-to-parent
                  (recur component-path* route-path* rst* #(add-to-parent ((fac) (merge-props {}) %)))
                  (recur component-path* route-path* rst* #((fac) (merge-props {}) %)))
                )
              (recur (pop component-path) route-path* rst* add-to-parent))))))))

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
               (let [default-query '[:root]]
                 (if-let [{:keys [route/id route/params]} @active-route]
                   (if-let [query (:query (get-active-query routes route-hierarchy id params))]
                     query
                     default-query)
                   default-query)))
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
                               el (get-element-tree @routes path root-props ModuleStatus)]

                           el))
                       )))]
       {:root-class AppRoot
        :set-route! set-active-route!
        :ui->props  (fn [env c]
                      (let [props (om/default-ui->props env c)]
                        (when props (merge props @active-route))))
        :get-route  (fn [] @active-route)}))))

(defn has-subroute
  [component]
  (if (first (om/children component))
    true
    false))

(defn render-subroute
  ([component]
   (render-subroute component {}))
  ([component more-computed]
   (let [[ui fac merge-props child] (first (om/children component))]
     (println ui)
     ((fac) (merge-props more-computed) child))))

(defn try-render-subroute
  ([component]
   (try-render-subroute component {}))
  ([component more-computed]
   (let [subroutef (first (om/children component))]
     (when subroutef (render-subroute component more-computed)))))








