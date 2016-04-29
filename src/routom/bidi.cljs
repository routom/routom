(ns routom.bidi
  (:require [bidi.bidi :as b]
            [clojure.string :as s]
            )
  (:import [goog.Uri QueryData]))

(defprotocol BidiRouter
  (unlisten [this])
  (href-for
    [this route-id route-params]
    [this route-id route-params query-params])
  (path-for
    [this route-id route-params]
    [this route-id route-params query-params]))

(defn routes->bidi-route
  ([routes]
   (let [bidi-routes (routes->bidi-route {:sub-routes routes} [])]
     ["" bidi-routes]))
  ([routes hierarchy]
   (let [sub-routes (get routes :sub-routes)]
     (reduce-kv
       (fn [hier k v]
         (let [path (:bidi/path v)
               new-hier [path k]]
           (if (contains? v :sub-routes)
             (if path
               (conj hier [path (into [["" k]] (routes->bidi-route v []))])
               (routes->bidi-route v hier))
             (conj hier new-hier)))) hierarchy sub-routes))))

(defn start-bidi-router!
  [history set-route! routes default-route]
  (let [routes-atom (atom (routes->bidi-route @routes))]
    (letfn [
            (token->location [token]
              (b/match-route @routes-atom token))
            (location->token [{:keys [route/id route/params]}]
              (b/unmatch-pair @routes-atom {:handler id
                                            :params  params}))
            (replace-current-location []
              (let [token (.getCurrentLocation history)
                    token (if (and (not (s/blank? token)) (token->location (.-pathname token)))
                            token
                            (location->token default-route))]
                (.replace history token)))
            (on-navigate [location]
              (let [{:keys [handler route-params]} (token->location (.-pathname location))]
                (if handler
                  (set-route!
                    {:route/id     handler
                     :route/params (merge (if-let [query (.-query location)]
                                            (js->clj query :keywordize-keys true)
                                            {}) route-params)})
                  (set-route!
                    {:route/id     :not-found
                     :route/params (js->clj location :keywordize-keys true)}))))]
      (let [unlisten (.listen history
                              on-navigate)]

        (replace-current-location)

        (add-watch routes :bidi/router
                   (fn [_ _ prev-state new-state]
                     (reset! routes-atom (routes->bidi-route new-state))
                     (replace-current-location)

                     ))

        (reify BidiRouter
          (unlisten [_]
            (unlisten)
            (remove-watch routes :bidi/router))
          (path-for [this route-id route-params]
            (path-for this route-id route-params nil))
          (path-for [_ route-id route-params query-params]
            (let [path (b/unmatch-pair @routes-atom {:handler route-id :params route-params})]
              (if query-params
                (let [qs (.createFromMap QueryData (clj->js (or query-params {})))]
                  (str path "?" qs))
                path)))
          (href-for [this route-id route-params]
            (href-for this route-id route-params nil))
          (href-for [this route-id route-params query-params]
            (.createHref history (path-for this route-id route-params query-params))))))))