(ns leiningen.deploy-deps-test
  (:require [leiningen.deploy-deps :refer (deploy-deps)]
            [leiningen.core.project :as project]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.main :as main]
            [clojure.java.io :as io])
  (:use clojure.test))

;; Fixtures

(def tmp-dir (io/as-file "test/tmp/"))

(defn- delete-tmp-dir []
  (doseq [f (reverse (file-seq tmp-dir))]
    (.delete f)))

(defn with-tmp-dir [f]
  (delete-tmp-dir)
  (f)
  (delete-tmp-dir))

(use-fixtures :each with-tmp-dir)


;; Helpers

(defn tmp-repo []
  (let [f (io/file tmp-dir (name (gensym "repo")))]
    (assert (not (.exists f)) (str "file-repo already exists at: " (.getPath f)))
    (.mkdirs f)
    (.toURI f)))

(defn tmp-proj []
  (let [f (io/file tmp-dir (name (gensym "proj")))]
    (assert (not (.exists f)) (str "Project root already exists at: " (.getPath f)))
    (.mkdirs f)
    (.getPath f)))

(defn dummy-project
  ([] (dummy-project {} [:base] []))
  ([project] (dummy-project project [:base] []))
  ([project include-profiles]
     (dummy-project project include-profiles []))
  ([project include-profiles exclude-profiles]
     (-> (apply project/init-profiles
                (merge project/defaults
                       {:root (tmp-proj)}
                       project)
                (into [:base] include-profiles)
                exclude-profiles)
         (project/init-project))))

(defn resolve-deps [proj]
  (classpath/resolve-dependencies :dependencies proj))

(defn deployed-jars [repo-uri]
  (->> (file-seq (io/as-file repo-uri))
       (filter #(not (.isDirectory %)))
       (filter #(.endsWith (.getName %) ".jar"))))

(defn deployed-poms [repo-uri]
  (->> (file-seq (io/as-file repo-uri))
       (filter #(.isFile %))
       (filter #(.endsWith (.getName %) ".pom"))))

(defn common-name [f]
  (let [name (.getName f)]
    (.substring name 0 (- (count name) (count ".jar")))))


;; Test

(deftest deploy-deps-test
  (let [repo-name "releases"
        repo-uri (tmp-repo)
        proj (dummy-project
              {:deploy-repositories [[repo-name {:url (str repo-uri)}]]
               :dependencies '[[org.clojure/clojure "1.4.0"]]})
        proj-jars (resolve-deps proj)
        repo-jars (deployed-jars repo-uri)
        repo-poms (deployed-poms repo-uri)]

    (deploy-deps proj repo-name)

    (is (= (set (map common-name proj-jars))
           (set (map common-name repo-jars)))
        "All jars were deployed")

    (is (= (set (map common-name proj-jars))
           (set (map common-name repo-poms)))
        "All poms were deployed")))

(deftest deploy-deps-snapshot-test
  (testing "release and snapshot deps.."
    (let [project
          {:repositories {"sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}}
           :dependencies '[[org.clojure/clojure "1.5.0-master-SNAPSHOT"]
                           [org.clojure/core.cache "0.6.2"]]}]

     (testing "w/ NO repository-name specified..."
       (let [snapshots-uri (tmp-repo)
             releases-uri (tmp-repo)
             proj (merge project
                         {:deploy-repositories
                          [["snapshots" {:url (str snapshots-uri) :snapshots true}]
                           ["releases" {:url (str releases-uri) :snapshots false}]]})
             snapshots-jars (deployed-jars snapshots-uri)
             releases-jars (deployed-jars releases-uri)]

         (deploy-deps proj)

         (is (= (count snapshots-jars) 1))
         (is (re-find #"org/clojure/clojure/1.5.0-master-SNAPSHOT/" (str (first snapshots-jars)))
             "Snapshot jars deployed to snapshots repo by default")

         (is (= (count releases-jars) 1))
         (is (re-find #"org/clojure/core.cache/0.6.2/" (str (first releases-jars)))
             "Release jars deployed to releases repo by default")))

     (testing "w/ 'releases' repository-name specified..."
       (let [snapshots-uri (tmp-repo)
             releases-uri (tmp-repo)
             proj (merge project
                         {:deploy-repositories
                          [["snapshots" {:url (str snapshots-uri) :snapshots true}]
                           ["releases" {:url (str releases-uri) :snapshots false}]]})
             snapshots-jars (deployed-jars snapshots-uri)
             releases-jars (deployed-jars releases-uri)]

         (deploy-deps proj "releases")

         (is (= (count snapshots-jars) 0)
             "No snapshot jars deployed")

         (is (= (count releases-jars) 1))
         (is (re-find #"org/clojure/core.cache/0.6.2/" (str (first releases-jars)))
             "Release jars deployed to releases repo"))))))