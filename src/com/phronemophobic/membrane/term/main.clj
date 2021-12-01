(ns ^:no-doc com.phronemophobic.membrane.term.main
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [com.phronemophobic.membrane.term :as term]
            [membrane.ui :as ui]
            [membrane.toolkit :as tk]
            [com.phronemophobic.membrane.term.color-scheme :as color-scheme]
            [docopt.core :as docopt]))

(def docopt-usage
  "membrane.term

Usage:
  membrane.term run-term [--width=<cols>] [--height=<rows>] \\
   [--color-scheme=<path>] [--font-family=<font>] [--font-size=<points>] [--toolkit=<toolkit>]
  membrane.term screenshot --play=<path> [--width=<cols>] [--height=<rows>] \\
   [--color-scheme=<path>] [--font-family=<font>] [--font-size=<points>]\\
   [--out=<file>] [--line-delay=<ms>] [--final-delay=<ms>]  [--toolkit=<toolkit>]
  membrane.term --help

Common Options:
  -w, --width=<cols>         Width in characters [default: 90]
  -h, --height=<rows>        Height in characters [default: 30]
      --color-scheme=<path>  Local path or url to iTerm .itermcolors scheme file, uses internal scheme by default.
      --font-family=<font>   Choose an OS installed font [default: monospace]
      --font-size=<points>   Specify the font point size [default: 12]
      --toolkit=<toolkit>    Specify the toolkit [default: java2d]

Screenshot Options:
  -p, --play=<path>          Path to script to play in terminal
  -o, --out=<file>           Filename of the image to generate [default: terminal.png]
      --line-delay=<ms>      Delay in ms to wait after each line is sent to the terminal [default: 1000]
      --final-delay=<ms>     Delay in ms to wait before writing the view of the terminal [default: 10000]

Replace membrane.term with your appropriate Clojure tools CLI launch sequence. For example:
|
| clojure -M:membrane.term run-term -w 133 -h 60
|")

(defn parse-font-family [v]
  (if (= "monospace" v)
    :monospace
    (str v)))

(defn- int-parser-validator [min]
  (fn [v]
    (let [num (if (integer? v)
                v
                (when (and (string? v) (re-matches #"\d+" v))
                  (Integer/parseInt v)))]
      (if (and num (>= num min))
        num
        {:error (format "expected positive integer >= %d" min)}))))

(defn- parse-existing-path [v]
  (when v
    (let [p (str v)]
      (try
        (with-open [_rdr (io/reader p)])
        p
        (catch Throwable e
          {:error (format "unable to open path, %s" (ex-message e))})))))

(defn- parse-color-scheme [v]
  (when v
    (let [p (parse-existing-path v)]
      (if (:error p)
        p
        (try
          (color-scheme/load-scheme p)
          (catch Throwable e
            {:error (format "Unable to load color-scheme from %s" p)
             :exception e}))))))

(defn- parse-image-out [v]
  (when v
    (let [p (str v)]
      (if (re-find #"(?i).+\.(png|jpeg|jpg|webp)$" p)
        p
        {:error "supported image formats are png, webp and jpeg (aka jpg)."}))))


(def valid-toolkits #{"java2d" "skia"})
(defn- load-toolkit [toolkit]
  (if-not (or (nil? toolkit)
              (valid-toolkits toolkit))
    (throw (ex-info (format "Invalid toolkit: %s. Valid toolkits are %s."
                            toolkit
                            (->> valid-toolkits
                                 (map #(str "\"" % "\""))
                                 (string/join ", ")))
                    {}))
    (case toolkit
      (nil "java2d")
      @(requiring-resolve 'membrane.java2d/toolkit)

      ("skia")
      @(requiring-resolve 'membrane.skia/toolkit))))

(defn parse-toolkit [v]
  (try
    (load-toolkit v)
    (catch Throwable e
      {:error (ex-message e)})))

(defn- validate-font [args]
  (let [font-family (get args "--font-family")
        font-size (get args "--font-size")
        toolkit (get args "--toolkit")
        font-family (if (keyword? font-family)
                      (tk/logical-font->font-family toolkit :monospace)
                      font-family)]
    (when-not (tk/font-exists? toolkit (ui/font font-family font-size))
      {:error (format "font family %s, size %d not found" font-family font-size)})))

(defn- validate-args [args args-def post-validations]
  (let [parsed-args (reduce-kv (fn [m k v]
                                 (let [od (get args-def k)
                                       v ((or od identity) v)]
                                   (if (:error v)
                                     (reduced (assoc v :error (format "%s: %s" k (:error v))))
                                     (assoc m k v))))
                               {}
                               args)]
    (if (:error parsed-args)
      parsed-args
      (if-let [validation-error (first (keep #(% parsed-args) post-validations))]
        validation-error
        parsed-args))))

(defn- keywordize [arg-map]
  (reduce-kv (fn [m k v]
               (if v
                 (assoc m (keyword (string/replace-first k #"^--" "")) v)
                 m))
             {}
             arg-map))

(defn- undo-line-continuations
  "Docopt does not seem to support line continuations, but I feel they make the usage help readable."
  [usage]
  (string/replace usage #"\\\R" ""))

(defn -main [& args]
  (docopt/docopt (undo-line-continuations docopt-usage) args
                 (fn result-fn [arg-map]
                   (let [arg-map (validate-args arg-map {"--width" (int-parser-validator 1)
                                                         "--height" (int-parser-validator 1)
                                                         "--play" parse-existing-path
                                                         "--color-scheme" parse-color-scheme
                                                         "--out" parse-image-out
                                                         "--line-delay" (int-parser-validator 0)
                                                         "--final-delay" (int-parser-validator 0)
                                                         "--font-family" parse-font-family
                                                         "--font-size" (int-parser-validator 1)
                                                         "--toolkit" parse-toolkit}
                                                [validate-font])]
                     (if-let [error (:error arg-map)]
                       (do
                         (println (format "*\n* Error: %s\n*" error))
                         (when-let [e (:exception arg-map)]
                           (println (format "* Exception: %s\n*" (ex-message e))))
                         (println (str "\n" docopt-usage))
                         (System/exit 1))
                       (let [opts (keywordize arg-map)
                             opts (update opts :font-family
                                          (fn [font-family]
                                            (if (keyword? font-family)
                                              (tk/logical-font->font-family (:toolkit opts) font-family)
                                              font-family)))]
                         (cond
                           (:help opts) (println docopt-usage)
                           (:run-term opts) (term/run-term opts)
                           :else (term/screenshot opts))))))
                 (fn usage-fn [_]
                   (println "*\n* Usage error\n*\n")
                   (println docopt-usage)
                   (System/exit 1)))
  (shutdown-agents))
