(ns routom.bidi
  (:require [bidi.bidi :as b]
            [clojure.string :as s]))

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