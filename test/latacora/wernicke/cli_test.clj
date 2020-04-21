(ns latacora.wernicke.cli-test
  {:clj-kondo/config {:linters {:private-call {:level :off}}}}
  (:require
   [latacora.wernicke.cli :as cli]
   [clojure.test :as t]
   [clojure.string :as str]
   [cheshire.core :as json]))

(t/deftest verbosity->log-level-tests
  (t/are [n expected] (= (cli/verbosity->log-level n) expected)
    0 :info

    -1 :warn
    -2 :error
    -3 :fatal

    1 :debug
    2 :trace

    ;; extreme cases
    -100 :fatal
    100 :trace))

(defmacro with-fake-exit
  [& body]
  `(with-redefs [cli/exit! (fn [message# code#]
                             (->> {:message message# :code code#}
                                  (ex-info "exit! called")
                                  (throw)))]
     (try
       {:exited? false :value ~@body}
       (catch clojure.lang.ExceptionInfo e#
         {:exited? true :value (ex-data e#)}))))

(defmacro with-captured-output
  [& body]
  `(let [out# (java.io.StringWriter.)
         err# (java.io.StringWriter.)]
     (binding [*out* out# *err* err#]
       ~@body
       {:out (str out#)
        :err (str err#)})))

(defn cli-test-harness
  [in-str args]
  (with-in-str in-str (with-fake-exit (with-captured-output (apply cli/-main args)))))

(def help-lines
  ["Redact structured data."
   ""
   "Usage: wernicke [OPTIONS] < infile > outfile"
   ""
   "Input is only read from stdin, output only written to stdout."
   ""
   "Options:"
   "  -h, --help                 display help message"
   "  -v, --verbose              increase verbosity"
   "  -i, --input FORMAT   json  input format (one of json, edn)"
   "  -o, --output FORMAT  json  output format (one of json, edn)"])

(def expected-help
  (str/join \newline help-lines))

(t/deftest cli-tests
  (t/is (= {:exited? true
            :value {:message expected-help :code 0}}
           (cli-test-harness "{}" ["--help"])))
  (t/is (= {:exited? true
            :value {:message (->> help-lines
                                  (into ["The following error occurred while parsing your command:"
                                         "Unknown option: \"--nonsense\""
                                         ""])
                                  (str/join \newline))
                    :code 1}}
           (cli-test-harness "{}" ["--nonsense"])))

  (let [data {:a 1}
        json (json/generate-string data)
        edn  (pr-str data)]
    (t/is (= {:exited? false
              :value {:out json
                      :err ""}}
             (cli-test-harness json []))
          "implicit json in, implicit json out")
    (t/is (= {:exited? false
              :value {:out json
                      :err ""}}
             (cli-test-harness json ["--input" "json"]))
          "explicit json in, implicit json out")
    (t/is (= {:exited? false
              :value {:out json
                      :err ""}}
             (cli-test-harness json ["--input=json"]))
          "explicit json in, implicit json out")
    (t/is (= {:exited? false
              :value {:out json
                      :err ""}}
             (cli-test-harness json ["--input" "json" "--output" "json"]))
          "explicit json in, explicit json out")

    (t/is (= {:exited? false
              :value {:out json
                      :err ""}}
             (cli-test-harness edn ["--input" "edn"])))
    (t/is (= {:exited? false
              :value {:out edn
                      :err ""}}
             (cli-test-harness edn ["--input" "edn" "--output" "edn"])))))
