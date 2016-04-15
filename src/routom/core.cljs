(ns routom.core
  (:require [om.next :as om]
            [routom.util :as u]))

(defprotocol IRootQuery
  (root-query [this] "Return the component's unbound query to be applied to the application's root component"))

(defn ^boolean irootquery? [x]
  (if (implements? IRootQuery x)
    true
    (when (goog/isFunction x)
      (let [x (js/Object.create (. x -prototype))]
        (implements? IRootQuery x)))))
