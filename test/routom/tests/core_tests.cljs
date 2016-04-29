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

(deftest get-route-query
  (testing "no params"
    (let [Route1 (ui
                   static om/IQuery
                   (query [this] [:a :b]))
          route-atom (atom {:root {:ui Route1}})]
      (is (= {:query  [:default {:root [:a :b]}]
              :params {}}
             (rt/get-route-query route-atom [:default] '(:root) {})))))
  (testing "with root query"
    (let [Route1 (ui
                   static rt/IRootQuery
                   (root-query [this] [:c :d])
                   static om/IQuery
                   (query [this] [:a :b]))
          route-atom (atom {:root {:ui Route1}})]
      (is (= {:query  [:default {:root [:a :b]} :c :d]
              :params {}}
             (rt/get-route-query route-atom [:default] '(:root) {})))))
  (testing "with params"
    (let [Route1 (ui
                   static om/IQueryParams
                   (params [this] {:p1 :e})
                   static rt/IRootQuery
                   (root-query [this] [:c :d])
                   static om/IQuery
                   (query [this] '[:a ?p1]))
          route-atom (atom {:root {:ui Route1}})]
      (is (= {:query  '[:default {:root [:a ?p1]} :c :d]
              :params {:p1 :e}}
             (rt/get-route-query route-atom [:default] '(:root) {})))
      (is (= {:query  '[:default {:root [:a ?p1]} :c :d]
              :params {:p1 :f}}
             (rt/get-route-query route-atom [:default] '(:root) {:p1 :f})))))
  (testing "with parent route"
    (let [Route1 (ui
                   static om/IQueryParams
                   (params [this] {:p1 :e})
                   static rt/IRootQuery
                   (root-query [this] [:c :d])
                   static om/IQuery
                   (query [this] '[:a ?p1]))
          Route2 (ui
                   static om/IQueryParams
                   (params [this] {:z :z})
                   static rt/IRootQuery
                   (root-query [this] '[:x ?z])
                   static om/IQuery
                   (query [this] '[:m :n]))
          route-atom (atom {:route1
                            {:ui Route1
                             :sub-routes
                                 {:route2 {:ui Route2}}}})]

      (is (= {:query  '[
                        ;default query
                        :default
                        ;route 2 query
                        {:route2 [:m :n]}
                        ;route 2 root query
                        :x ?z
                        ;route 1 query
                        {:route1 [:a ?p1]}
                        ;route 1 root query
                        :c :d]
              :params {:z  :z
                       :p1 :e}}
             (rt/get-route-query route-atom [:default] '(:route1 :route2) {})))
      (is (= {:query  '[
                        ;default query
                        :default
                        ;route 2 query
                        {:route2 [:m :n]}
                        ;route 2 root query
                        :x ?z
                        ;route 1 query
                        {:route1 [:a ?p1]}
                        ;route 1 root query
                        :c :d]
              :params {:z  :zz
                       :p1 :p1p1}}
             (rt/get-route-query route-atom [:default] '(:route1 :route2) {:z :zz :p1 :p1p1}))))))