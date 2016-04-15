(ns routom.tests.core-tests
  (:require [cljs.test :refer-macros [deftest is are testing run-tests]]
            [om.next :as om :refer-macros [defui ui]]
            [cljsjs.react]
            [routom.core :as rt]))


(deftest test-irootquery?
  (is (rt/irootquery? (ui
                        static rt/IRootQuery
                        (root-query [this] [:a :b])))))

(deftest test-not-irootquery?
  (is (not (rt/irootquery? (ui)))))