(ns com.phronemophobic.membrane.term.color-scheme
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zxml]
            [clojure.set :as cset]
            [clojure.java.io :as io]
            [clojure.zip :as zip]))

(def ^:private iterm-color
  ;; iterm keys currently of interest to us, others can be added if/when we add support in membrane.term
  {"Ansi 0 Color"        :black
   "Ansi 1 Color"        :red
   "Ansi 2 Color"        :green
   "Ansi 3 Color"        :yellow
   "Ansi 4 Color"        :blue
   "Ansi 5 Color"        :magenta
   "Ansi 6 Color"        :cyan
   "Ansi 7 Color"        :white
   "Ansi 8 Color"        :bright-black
   "Ansi 9 Color"        :bright-red
   "Ansi 10 Color"       :bright-green
   "Ansi 11 Color"       :bright-yellow
   "Ansi 12 Color"       :bright-blue
   "Ansi 13 Color"       :bright-magenta
   "Ansi 14 Color"       :bright-cyan
   "Ansi 15 Color"       :bright-white
   "Cursor Color"        :cursor
   "Cursor Text Color"   :cursor-text
   "Background Color"    :background
   "Foreground Color"    :foreground})

(def ^:private required-colors (cset/map-invert iterm-color))

(def ^:private iterm-component
  {"Red Component" :red
   "Green Component" :green
   "Blue Component" :blue
   "Alpha Component" :alpha})

(def ^:private required-components (select-keys (cset/map-invert iterm-component)
                                                [:red :green :blue]))

(defn- iterm-color->rgb [ctx zdict]
  (let [cmap (->> (iterate #(-> % zip/right zip/right) (zip/down zdict))
                  (take-while identity)
                  (reduce (fn [acc z]
                            (let [cin  (-> z zxml/text)]
                              (if-let [ckey (get iterm-component cin)]
                                (assoc acc ckey (-> z zip/right zxml/text Double/parseDouble))
                                acc)))
                          {}))
        missing-components (apply dissoc required-components (keys cmap))]
    (if (seq missing-components)
      (throw (ex-info (format "%s %s is missing components: %s" (:source ctx) (pr-str (:context ctx)) (vec (vals missing-components))) {}))
      (if (:alpha cmap)
        ((juxt :red :green :blue :alpha) cmap)
        ((juxt :red :green :blue) cmap)))))

(defn load-scheme
  "We currently support iTerm scheme format which is held in Apple Info.plist XML.

  `source` can be whatever `clojure.java.io/reader` accepts, common usages:
  - local path `\"./Solarized Dark.itermcolors\"` or
  - a url `\"https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/master/schemes/Dracula%2B.itermcolors\"`"
  [source]
  (with-open [rdr (io/reader source)]
    (let [z (-> rdr
                (xml/parse :namespace-aware false :skip-whitespace true)
                zip/xml-zip)
          scheme (reduce (fn [acc z]
                           (let [cin (-> z zxml/text)]
                             (if-let [color-name (get iterm-color cin)]
                               (let [rgba (->> z zip/right (iterm-color->rgb {:context cin :source source}))]
                                 (assoc acc color-name rgba))
                               acc)))
                         {}
                         (->> (iterate #(-> % zip/right zip/right) (zxml/xml1-> z :dict :key))
                              (take-while identity)))
          missing-keys (apply dissoc required-colors (keys scheme))]
      (if (seq missing-keys)
        (throw (ex-info (format "%s is missing colors: %s" source (vec (vals missing-keys))) {}))
        scheme))))
