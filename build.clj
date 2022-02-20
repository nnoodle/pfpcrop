(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str])
  (:import [com.gluonhq.substrate ProjectConfiguration SubstrateDispatcher]
           com.gluonhq.substrate.model.Triplet
           java.nio.file.Path
           java.io.File))

(def lib 'io.github.nnoodle/pfpcrop)
(def version "0.1.0-SNAPSHOT")
(def main 'pfpcrop.PfpCrop)

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile-java [_]
  (b/javac {:src-dirs ["src/main/java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["--module-path" ;; "/usr/share/openjfx/lib" ; tools.build doesn't handle modules (yet)
                         (->> basis :classpath-roots rest (str/join (System/getProperty "path.separator")))
                         "--release" "11"]}))

(defn uber "build a fatjar" [_]
  (compile-java nil)
  (b/copy-dir {:src-dirs ["src/main/java" "src/main/resources"]
               :target-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'pfpcrop.NonModular}))

(defn native "build a native image using Gluon Substrate" [_]
  (compile-java nil)
  (b/copy-dir {:src-dirs ["src/main/resources"]
               :target-dir class-dir})
  (let [class-dir (.toAbsolutePath (Path/of class-dir (into-array String [])))
        classpath (str/join File/pathSeparator
                            (conj (mapcat :paths (-> basis :libs vals))
                                  (.toString class-dir)))
        substrate-project-configuration
        ;; documentation: https://docs.gluonhq.com/#_configuration
        (doto (ProjectConfiguration. (name main) classpath)
          (.setTarget (Triplet/fromCurrentOS))
          (.setGraalPath (Path/of (System/getenv "GRAALVM_HOME")
                                  (into-array String [])))
          (.setAppId (format "%s.%s" (namespace lib) (name lib)))
          (.setVerbose true)
          (.setAppName (name lib)))
        substrate-dispatcher
        (SubstrateDispatcher. (.toAbsolutePath (Path/of "target" (into-array ["gluonfx"])))
                              substrate-project-configuration)]
    (.nativeCompile substrate-dispatcher)
    (.nativeLink substrate-dispatcher)))
