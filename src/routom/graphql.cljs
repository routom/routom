(ns routom.graphql
  (:require [om.next :as om]
            [clojure.string :as string]))

(def ^:private DEFAULT-INDENT "  ")

(defn ^:private format-value [v]
  (cond
    (string? v) (str "\"" v "\"")
    (keyword? v) (name v)
    :else v))

(defn ast->graphql
  "Converts an Om-Next query's abstract syntax tree
  to a GraphQL query"
  ([ast]
   (ast->graphql "" DEFAULT-INDENT ast))
  ([current-indent indent {:keys [type children key params] :as ast}]
   (letfn [(children->graph-ql []
             (if children
               (str (string/join (str "\n" indent) (map (partial ast->graphql indent (str indent DEFAULT-INDENT)) children))
                    "\n" current-indent "}")
               ""))
           (params->args []
             (if (seq params)
               (str (name key) "(" (string/join ", " (map (fn [[k v]] (str (name k) ": " (format-value v))) params)) ")")
               (name key)))]
     ; TODO handle mutations
     (condp = type
       :root (str "{\n" indent
                  (children->graph-ql))
       :join (str (params->args) " {\n" indent (children->graph-ql))
       :prop (params->args)))))

(defn query->graphql [query]
  "Converts an Om-Next query
  to a GraphQL query"
  (ast->graphql (om/query->ast query)))


