(ns routom.tests.util-tests
  (:require [cljs.test :refer-macros [deftest is are testing run-tests]]
            [om.next :as om :refer-macros [defui ui]]
            [cljsjs.react]
            [routom.util :as rt]))






(deftest test-get-path
  (let [hierarchy {:root       nil
                   :child      :root
                   :grandchild :child
                   :other :root
                   :foo :child}]

    (testing "preconditions"
      (testing "hierarchy"
        (is (try
              (rt/get-path [] :foo)
              false
              (catch js/Object e
                true)))))
    (testing "grandchild"
      (is (= '(:root :child :grandchild) (rt/get-path hierarchy :grandchild))))
    (testing "invalid key"
      (is (try
            (rt/get-path hierarchy :invalid)
            false
            (catch js/Object e
              (= :routom/invalid-route-id (get (.-data e) :type) )))))))

(deftest path->keys
  (is (= (rt/path->keys [:a :b]) [:a :sub-routes :b]))
  (is (= (rt/path->keys [:a :b :c]) [:a :sub-routes :b :sub-routes :c])))

(deftest get-route
  (let [routes {:root
               {:sub-routes
                {:home
                 {:ui nil}}}}]
    (is (= (rt/get-route routes
                         '(:root :home))
           {:ui nil}))
    (is (= (rt/get-route routes
                         '(:invalid :test))
           nil))))

(deftest create-hierarchy
  (let [routes {:root
                {:sub-routes
                 {:home
                  {:sub-routes
                   {:page1 nil}}}}}
        hierarchy (rt/get-hierarchy routes)]
    (is (= (keys hierarchy) '(:root :home :page1)))
    (is (= (:root hierarchy) nil))
    (is (= (:home hierarchy) :root))
    (is (= (:page1 hierarchy) :home))))





