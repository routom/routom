(ns ^:figwheel-no-load routom.tests.run-tests
  (:require [cljs.test :refer-macros [run-tests]]
            [routom.tests.util-tests]
            [routom.tests.core-tests]))

(enable-console-print!)

(defn main []
  (run-tests 'routom.tests.util-tests
             'routom.tests.core-tests))

(defn on-js-reload []
  (.log js/console "reloading tests")
  (main))

(main)