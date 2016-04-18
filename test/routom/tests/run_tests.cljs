(ns ^:figwheel-no-load routom.tests.run-tests
  (:require [cljs.test :refer-macros [run-tests]]
            [routom.tests.util-tests]
            [routom.tests.core-tests]
            [routom.tests.bidi-tests]))

(enable-console-print!)

(defn main []
  (run-tests 'routom.tests.util-tests
             'routom.tests.core-tests
             'routom.tests.bidi-tests))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "Success!")
    (println "FAIL")))

(defn on-js-reload []
  (.log js/console "reloading tests")
  (main))

(main)