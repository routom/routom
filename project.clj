(defproject org.routom/routom "0.1.0-alpha10"
  :description " An Om.Next routing solution for the browser and React-Native"
  :url "https://github.com/routom/routom"
  :license {:name "The Unlicense"
            :url "http://unlicense.org/UNLICENSE"}

  :min-lein-version "2.6.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.omcljs/om "1.0.0-alpha46"]
                 [bidi "2.0.4"]
                 [org.clojure/core.async "0.2.395"
                  :exclusions [org.clojure/tools.reader]]
                 [datascript "0.15.5"]]


  :checkout-deps-shares ^:replace [:source-paths :resource-paths :compile-path
                                  #=(eval leiningen.core.classpath/checkout-deps-paths)]

  :plugins [[lein-figwheel "0.5.8"]
            [lein-cljsbuild "1.1.4" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                ;; If no code is to be run, set :figwheel true for continued automagical reloading
                :figwheel {:on-jsload "routom.core/on-js-reload"}

                :compiler {:main routom.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/routom.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}

               {:id "test"
                :source-paths ["src" "test"]

                ;; If no code is to be run, set :figwheel true for continued automagical reloading
                :figwheel {:on-jsload routom.tests.run-tests/on-js-reload}

                :compiler {:main routom.tests.run-tests
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/routom.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/routom.js"
                           :main routom.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             :server-port 3479 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"]}) ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"

