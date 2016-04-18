(ns routom.tests.bidi-tests
  (:require [cljs.test :refer-macros [deftest is are testing run-tests]]
            [routom.bidi :as rb]))

(deftest routes->bidi-route
  (is (=
        ["" [["/page1" :page1] ["/page2" :page2]]]
        (rb/routes->bidi-route
          {:page1
           {:bidi/path "/page1"}
           :page2
           {:bidi/path "/page2"}})
        ))
  (is (=
        [""
         [["/"
           [["" :root]
            ["page1/" :page1]
            ["page2/" :page2]]]]]
        (rb/routes->bidi-route
          {:root
           {:bidi/path "/"
            :sub-routes
                       {:page1
                        {:bidi/path "page1/"}
                        :page2
                        {:bidi/path "page2/"}}}
           })))
  (is (=
        [""
         [
          ["/page1" :page1]
          ["/page2" :page2]]]
        (rb/routes->bidi-route
          {:root
           {:sub-routes
            {:page1
             {:bidi/path "/page1"}
             :page2
             {:bidi/path "/page2"}}}
           }))))