(ns routom.util
  (:require [om.next :as om]))

(defn- get-path
  [hierarchy name]
  {:pre [(map? hierarchy)]}
  (if (not (contains? hierarchy name))
    (throw (ex-info (str "No route exists for route id " name)
                    {:type :routom/invalid-route-id}))
    (loop [node (get hierarchy name)
           path `(~name)]
      (if (not node)
        path
        (recur (get hierarchy node) (cons node path))))))

(def children-key :sub-routes)

(defn- path->keys
  [path]
  (interpose children-key path))

(defn- get-route
  [route-map path]
  {:pre [(map? route-map) (seq? path)]}
  (let [keys (path->keys path)]
    (get-in route-map keys)))

(defn- create-hierarchy
  ([routes]
   (create-hierarchy {children-key routes} nil {}))
  ([routes parent hierarchy]
   (reduce-kv
     (fn [hier k v]
       (when (contains? hier k)
         (throw (ex-info (str "route id " k " must be unique in the route tree")
                         {:type :routom/duplicate-route-id})))
       (let [new-hier (assoc hier k parent)]
         (if (contains? v children-key)
           (create-hierarchy v k new-hier)
           new-hier))) hierarchy (get routes children-key))))