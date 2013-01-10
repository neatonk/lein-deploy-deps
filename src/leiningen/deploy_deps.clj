(ns leiningen.deploy-deps
  (:require [cemerick.pomegranate.aether :as aether]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.main :as main]
            [leiningen.deploy :refer (repo-for)]
            [clojure.java.io :refer (file)]
            [clojure.string :as str]))

;; borrowed from leiningen.deploy
(defn- abort-message [message]
  (cond (re-find #"Return code is 405" message)
        (str message "\n" "Ensure you are deploying over SSL.")
        (re-find #"Return code is 401" message)
        (str message "\n" "See `lein help deploy` for an explanation of how to"
             " specify credentials.")
        :else message))


;; alias get-dependencies because it is priavte...
(def get-dependencies #'leiningen.core.classpath/get-dependencies)

(defn dependency-objs [graph]
  (->> graph keys (map (comp :dependency meta)) (remove nil?)))

(defn deps-for [project]
  (->> (get-dependencies :dependencies project)
       (dependency-objs)))


(defn jars-for [deps]
  (->> (map #(.getFile (.getArtifact %)) deps)
       (filter #(re-find #"\.jar$" (.getName %)))))


(defn- pom-file [^java.io.File jar]
  (file (.getParent jar) (str/replace (.getName jar) #"\.jar" ".pom")))

(defn poms-for [jars]
  (->> (map pom-file jars)
       (filter #(.exists %))))


(defn- dependency-coords [dep]
  (let [art (.getArtifact dep)]
   {:group (.getGroupId art)
    :artifact (.getArtifactId art)
    :version (.getVersion art)
    :classifier (.getClassifier art)
    :extension (.getExtension art)}))

(defn- lein-coords
  [{:keys [group artifact version classifier extenstion] :as opts}]
  [(symbol group artifact) version (select-keys opts [:classifier :extension])])

;; Use opts to override fields
(defn coords-for [deps & [opts]]
  (->> (map dependency-coords deps)
       (map #(merge % opts))
       (map lein-coords)))


(defn files-for [project]
  (let [deps (deps-for project)
        jars (jars-for deps)
        poms (poms-for jars)]
    (assert (= (count deps) (count jars) (count poms)))
    (merge (zipmap (coords-for deps {:extension "jar"}) jars)
           (zipmap (coords-for deps {:extension "pom"}) poms))))


(defn deploy-deps
  "Deploy project dependencies to a remote repository."
  [project & [repository-name]]
  (let [repo (repo-for project repository-name)
        files (files-for project)]
    (try
      (main/debug "Deploying" files "to" repo)
      (aether/deploy-artifacts :artifacts (keys files)
                               :files files
                               :transfer-listener :stdout
                               :repository [repo])
      (catch org.sonatype.aether.deployment.DeploymentException e
        (when main/*debug* (.printStackTrace e))
        (main/abort (abort-message (.getMessage e)))))))