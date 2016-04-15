(ns routom.util
  (:require [om.next :as om]))

(defn- get-path
  [hierarchy name]
  {:pre [(map? hierarchy)]}
  (let [parent (get hierarchy name)]
    (if (not parent)
      (throw (ex-info (str "No route exists for route id " name)
                      {:type :routom/invalid-route-id}))
      (loop [node parent
             path `(~name)]
        (if (not node)
          path
          (recur (get hierarchy node) (cons node path)))))))

(def children-key :sub-routes)

(defn- path->keys
  [path]
  (interpose children-key path))

(defn- get-route
  [route-map path]
  {:pre [(map? route-map) (seq? path)]}
  (let [keys (path->keys path)]
    (get-in route-map keys)))

(defn- get-hierarchy
  ([routes]
   (get-hierarchy {children-key routes} nil {}))
  ([routes parent hierarchy]
   (reduce-kv
     (fn [hier k v]
       (let [new-hier (assoc hier k parent)]
         (if (contains? v children-key)
           (get-hierarchy v k new-hier)
           new-hier))) hierarchy (get routes children-key))))