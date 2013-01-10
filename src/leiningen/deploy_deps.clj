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


(defn snapshot-jar? [jar-file]
  (re-find #"SNAPSHOT" (.getName jar-file)))

;;TODO: is this logic already available in leiningen.core?
(defn- default-repo-name [jar-file]
  (if (snapshot-jar? jar-file)
    "snapshots"
    "releases"))

(defn repo-for
  "Returns the repository for project matching repository-name (if
  given) or the correct default repository for the jar-file (if
  given). Otherwise returns nil."
  [project {:keys [jar-file repository-name]}]
  (cond repository-name (deploy/repo-for project repository-name)
        jar-file (deploy/repo-for project (default-repo-name jar-file))))


;; Alias get-dependencies because it is priavte...
(def get-dependencies #'leiningen.core.classpath/get-dependencies)

(defn deps-for [project]
  (keys (get-dependencies :dependencies project)))

(defn jars-for [deps] (map (comp :file meta) deps))

(defn poms-for [jars]
  (map #(io/file (.getParent %) (str/replace (.getName %) #"\.jar" ".pom"))
       jars))


(defn files-for
  "Returns a lazy seq of dependency file maps ready for deploy."
  [project]
  (let [deps (deps-for project)
        jars (map (comp :file meta) deps)
        poms (poms-for jars)]
    (main/debug "Dependencies for: " project "\n\n" deps)
    (assert (not-any? nil? (concat deps jars poms)))
    (map (fn [dep jar pom]
           {:coordinates dep
            :jar-file jar
            :pom-file pom})
         deps jars poms)))


;; Copied from leiningen.deploy source to fix detection of file uri's
;; and avoid returning nil settings.
;; TODO: create a pull request for this!
(defn add-auth-interactively [[id settings]]
  (if (or (and (:username settings) (some settings [:password :passphrase :private-key-file]))
          (.startsWith (:url settings) "file:/"))
    [id settings]
    (do
      (println "No credentials found for" id)
      (println "See `lein help deploying` for how to configure credentials.")
      (print "Username: ") (flush)
      (let [username (read-line)
            password (.readPassword (System/console) "%s"
                                    (into-array ["Password: "]))]
        [id (assoc settings :username username :password password)]))))


(defn deploy-deps
  "Deploy project dependencies to a remote repository."
  [project & [repository-name]]
  (with-redefs [deploy/add-auth-interactively add-auth-interactively]
    (let [repo (repo-for project {:repository-name repository-name})]
      (try
        (doseq [files (files-for project)]
          (let [repo (or repo (repo-for project files))]
            (main/debug "Deploying" files "to" repo)
            (apply aether/deploy
                   (apply concat
                          [:transfer-listener :stdout
                           :repository [repo]]
                          files))))
        (catch org.sonatype.aether.deployment.DeploymentException e
          (when main/*debug* (.printStackTrace e))
          (main/abort (abort-message (.getMessage e))))))))