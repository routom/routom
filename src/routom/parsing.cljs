(ns routom.parsing
  (:require [om.next :as om]
            [om.util]
            [clojure.walk :refer [postwalk]]))

(defn targeted-dispatch [{:keys [target]} key _] [key target])
(defn dispatch [_ key _] (if (om.util/ident? key)
                                          (first key)
                                          key))

(defmulti read-value dispatch)
(defmulti read-target targeted-dispatch)
(defmulti read-forced-target targeted-dispatch)

(defmethod read-target :default
  [{target :target} key params]
  {target false})

(defmethod read-forced-target :default
  [{{target :target} :ast} key params]
  {target true})

(defn read-fn
  "An Om.Next parser-compatible read function
  that conditionally dispatches to one of three multi methods
   1. read-forced-target is (-> env :ast :target) is not nil. See om/force for more about this scenario.
   2. read-target if (-> env :target) is not nil
   3. otherwise, read-value"
  [{target :target {forced-target :target} :ast :as env} key params]
  (cond
    forced-target (when (= forced-target target)
                    (read-forced-target env key params))
    target (read-target env key params)
    :else (read-value env key params))
  )

(defmulti mutate dispatch)
(defmulti mutate-target targeted-dispatch)
(defmulti mutate-forced-target targeted-dispatch)

(defmethod mutate-target :default
  [{target :target} key params]
  {target false})

(defmethod mutate-forced-target :default
  [{{target :target} :ast} key params]
  {target true})

(defn mutate-fn
  [{target :target {forced-target :target} :ast :as env} key params]
  (cond
    forced-target (when (= forced-target target)
                    (mutate-forced-target env key params))
    target (mutate-target env key params)
    :else (mutate env key params)))

(def parser (om/parser {:read read-fn :mutate mutate-fn}))

(defn filter-ast
  "removes props and joins from the specified ast
  where the key's namespace matches the regular expressions"
  [ast & res]
  (postwalk
    (fn [{:keys [children] :as x}]
      (if children
        (update x :children #(remove (fn [{:keys [key]}]
                                       (if-let [ns (namespace key)]
                                         (some (fn [re] (re-find re ns)) res))) %))
        x))
    ast))

(defn wrap-ast-filtering
  "Wraps an Om.Next parser function and
  removes join and prop expressions
  from remote queries when the namespace
  of the expression's keyword matches the
  specified regular expressions"
  [parse-fn & regexps]
  (if regexps
    (fn parse
      ([env query]
       (parse env query nil))
      ([env query target]
       (let [res (parse-fn env query target)]
         (if (and regexps (vector? res))
           (let [ast* (om/query->ast res)
                 ast* (apply (partial filter-ast ast*) regexps)]
             (om/ast->query ast*))
           res))))
    parse-fn))



