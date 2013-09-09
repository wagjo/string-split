(defproject com.wagjo/string-split "0.1.0-SNAPSHOT"
  :description "Various ways to split a string."
  :url "https://github.com/wagjo/string-split"
  :dependencies [[org.clojure/data.generators "0.1.2"]
                 [org.clojure/clojure "1.5.1"]
                 [core.async "0.1.0-SNAPSHOT"]
                 [criterium "0.4.2"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/jvm"]
  :global-vars {*warn-on-reflection* true}
  :repl-options {:port 41817
                 :host "0.0.0.0"}
;  :aot [wagjo.split.core]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as ClojureScript"}
  :jvm-opts ^:replace [#_"-Xms8G" #_"-Xmx8G" "-XX:-UseConcMarkSweepGC" "-server"]
)
