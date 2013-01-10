(ns leiningen.deploy-deps
  (:require [cemerick.pomegranate.aether :as aether]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]
            [leiningen.deploy :as deploy]
            [leiningen.jar :as jar]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; borrowed from leiningen.deploy
(defn- abort-message [message]
  (cond (re-find #"Return code is 405" message)
        (str message "\n" "Ensure you are deploying over SSL.")
        (re-find #"Return code is 401" message)
        (str message "\n" "See `lein help deploy` for an explanation of how to"
             " specify credentials.")
        :else message))


;; Alias get-dependencies because it is priavte...
(def get-dependencies #'leiningen.core.classpath/get-dependencies)

(defn deps-for [project]
  (keys (get-dependencies :dependencies project)))

(defn jars-for [deps] (map (comp :file meta) deps))

(defn poms-for [jars]
  (map #(io/file (.getParent %) (str/replace (.getName %) #"\.jar" ".pom"))
       jars))


(defn files-for
  "Returns a lazy seq of dependency file info as kvs ready for deploy."
  [project]
  (let [deps (deps-for project)
        jars (map (comp :file meta) deps)
        poms (poms-for jars)]
    (assert (= (count deps) (count jars) (count poms)))
    (map (fn [dep jar pom]
           [:coordinates dep
            :jar-file jar
            :pom-file pom])
         deps jars poms)))

(defn deploy-deps
  "Deploy project dependencies to a remote repository."
  [project & [repository-name]]
  (let [repo (deploy/repo-for project repository-name)]
    (main/debug "Deploying deps for" (pr-str project) "to" (pr-str repo))
    (try
      (doseq [files (files-for project)]
        (main/debug "Deploying" files "to" repo)
        (apply aether/deploy
               :transfer-listener :stdout
               :repository [repo]
               files))
      (catch org.sonatype.aether.deployment.DeploymentException e
        (when main/*debug* (.printStackTrace e))
        (main/abort (abort-message (.getMessage e)))))))