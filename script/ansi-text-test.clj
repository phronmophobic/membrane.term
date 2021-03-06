#!/usr/bin/env bb

(ns ansi-text-test
  "A little script to print out some sgr code decorated text for manual visual review.
   An attempt was made to fit entire output on a reasonably sized screen."

  (:require [clojure.set :as cset]
            [clojure.string :as string]))

;; from https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters
(def sgr-codes {:clear 0
                :bold 1
                :dim 2
                :italic 3
                :underline 4
                :slow-blink 5
                :rapid-blink 6
                :inverted 7
                :hide 8
                :strikeout 9
                :primary-font 10
                :alt-font-1 11
                :alt-font-2 12
                :alt-font-3 13
                :alt-font-4 14
                :alt-font-5 15
                :alt-font-6 16
                :alt-font-7 17
                :alt-font-8 18
                :alt-font-9 19
                :fraktur 20
                :not-bold 21 ;; or double underline sometimes
                :normal-intensity 22
                :not-italic 23
                :not-underlined 24
                :not-blinking 25
                :proportional 26
                :not-inverted 27
                :reveal 28
                :not-strikeout 29
                :fg-black 30
                :fg-red 31
                :fg-green 32
                :fg-yellow 33
                :fg-blue 34
                :fg-magenta 35
                :fg-cyan 36
                :fg-white 37
                :fg-rgb 38
                :fg-default 39
                :bg-black 40
                :bg-red 41
                :bg-green 42
                :bg-yellow 43
                :bg-blue 44
                :bg-magenta 45
                :bg-cyan 46
                :bg-white 47
                :bg-rgb 48
                :bg-default 49

                :disable-proportional 50

                :framed 51
                :encircled 52
                :overlined 53
                :not-framed-nor-circled 54
                :not-overlined 55

                :underline-color 58
                :default-underline-color 59

                :ideogram-underline 60
                :ideogram-double-underline 61
                :ideogram-overline 62
                :ideogram-double-overline 63
                :ideogram-stress 64
                :ideogram-clear 65

                :superscript 73
                :subscript 74
                :not-super-sub-script 75

                :fg-grey 90
                :fg-bright-red 91
                :fg-bright-green 92
                :fg-bright-yellow 93
                :fg-bright-blue 94
                :fg-bright-magenta 95
                :fg-bright-cyan 96
                :fg-bright-white 97

                :bg-grey 100
                :bg-bright-red 101
                :bg-bright-green 102
                :bg-bright-yellow 103
                :bg-bright-blue 104
                :bg-bright-magenta 105
                :bg-bright-cyan 106
                :bg-bright-white 107})


(def keys-visited (atom #{}))

(def isgr-codes (cset/map-invert sgr-codes))

;; to protect myself from myself accces maps from these fns only:
(defn- get-sgr-code [k]
  (let [code (or (get sgr-codes k) (throw (ex-info (format "oops key %s not defined" k) {})))]
    (swap! keys-visited conj k)
    code))
(defn- get-sgr-key [code]
  (let [k (or (get isgr-codes code) (throw (ex-info (format "oops code %s not defined" code) {})))]
    (swap! keys-visited conj k)
    k))

(defn- ansi [code & params]
  (str "\u001b[" (string/join ";" (concat [code] params)) "m"))

(defn- ansi-by-key [k & params]
  (apply ansi (get-sgr-code k) params))

(defn- sample-text [k & params]
  (let [lbl (name k)
        code (get-sgr-code k)]
    (format "%s%03d%s-%s "
            (apply ansi code params)
            code
            (if (seq params)
              (str ";" (string/join ";" params))
              "")
            lbl)))

(defn sample-text-for-codes [codes]
  (map (fn [c]
         (let [k (get-sgr-key c)]
           (str (sample-text k) (ansi-by-key :clear))))
       codes))

(defn sample-text-for-keys [ks]
  (sample-text-for-codes
    (map get-sgr-code ks)))

(defn- stripped [s]
  (string/replace s #"\u001b\[.*?m" ""))

(defn print-samples [title samples]
  (print (format "\n> %s\n  " title))
  (let [max-len (->> samples
                     (map stripped)
                     (map count)
                     (reduce max))
        col-width (+ max-len 4)
        screen-width 120
        num-cols (if (> col-width screen-width) 1 (int (/ screen-width col-width)))
        col-width (/ screen-width num-cols)]
    (->> samples
         (map (fn [s] (let [len (count (stripped s))
                            pad (max 0 (- col-width len 2))]
                        (str "[" s
                             (apply str (repeat pad " "))
                             "]"))))
         (partition num-cols num-cols nil)
         (interpose ["\n  "])
         (apply concat)
         (run! print)))
  (println))

(print-samples "membrane.term - supported"
               (sample-text-for-keys
                [:clear
                 :bold
                 :italic
                 :normal-intensity
                 :not-italic
                 :fg-black
                 :fg-red
                 :fg-green
                 :fg-yellow
                 :fg-blue
                 :fg-magenta
                 :fg-cyan
                 :fg-white
                 :fg-rgb
                 :fg-default
                 :bg-black
                 :bg-red
                 :bg-green
                 :bg-yellow
                 :bg-blue
                 :bg-magenta
                 :bg-cyan
                 :bg-white
                 :bg-rgb
                 :bg-default
                 :fg-grey
                 :fg-bright-red
                 :fg-bright-green
                 :fg-bright-yellow
                 :fg-bright-blue
                 :fg-bright-magenta
                 :fg-bright-cyan
                 :fg-bright-white
                 :bg-grey
                 :bg-bright-red
                 :bg-bright-green
                 :bg-bright-yellow
                 :bg-bright-blue
                 :bg-bright-magenta
                 :bg-bright-cyan
                 :bg-bright-white]))

(print-samples "membrane.term - questionable, 21 treated as not-bold, iTerm2 ignores, GNOME double underlines"
               [(str (sample-text :bold)
                     (sample-text :not-bold)
                     (ansi-by-key :clear))])

(print-samples "membrane.term - interesting candidates"
               (sample-text-for-keys
                [:dim
                 :underline
                 :slow-blink
                 :inverted
                 :strikeout
                 :not-underlined
                 :not-blinking
                 :not-inverted
                 :not-strikeout]))

(print-samples "membrane.term - maybe someday?"
               (sample-text-for-keys
                [:rapid-blink
                 :hide
                 :reveal
                 :overlined
                 :not-overlined
                 :underline-color
                 :default-underline-color]))

(print-samples "sanity on/off tests"
               [(str (sample-text :bold)
                     (sample-text :dim)
                     (sample-text :normal-intensity)
                     (ansi-by-key :clear))
                (str (sample-text :italic)
                     (sample-text :not-italic)
                     (ansi-by-key :clear))
                (str (sample-text :underline)
                     (sample-text :not-underlined)
                     (ansi-by-key :clear))
                (str (sample-text :slow-blink)
                     (sample-text :not-blinking)
                     (ansi-by-key :clear))
                (str (sample-text :fg-red)
                     (sample-text :inverted)
                     (ansi-by-key :clear))
                (str (sample-text :inverted)
                     (sample-text :not-inverted)
                     (ansi-by-key :clear))
                (str (sample-text :strikeout)
                     (sample-text :not-strikeout)
                     (ansi-by-key :clear))])

(print-samples "sanity rgb tests"
               [(str (sample-text :bg-rgb 5 130)
                     (ansi-by-key :clear))
                (str (sample-text :bg-rgb 2 100 200 43)
                     (ansi-by-key :clear))
                (str (sample-text :fg-rgb 5 33)
                     (sample-text :bg-rgb 5 192)
                     (sample-text :bg-default)
                     (ansi-by-key :clear))])

(print-samples "interesting, these work for GNOME Terminal"
               [(str (sample-text :rapid-blink)
                     (sample-text :not-blinking)
                     (ansi-by-key :clear))
                (str (sample-text :overlined)
                     (sample-text :not-overlined)
                     (ansi-by-key :clear))
                (str (sample-text :underline)
                     (sample-text :underline-color 5 130)
                     (sample-text :underline-color 2 52 180 235)
                     (sample-text :default-underline-color)
                     (ansi-by-key :clear))])

(print-samples "who supports hide/reveal? GNOME Terminal!"
               [(str (sample-text :hide)
                     (sample-text :reveal)
                     (ansi-by-key :clear))])

(print-samples "codes expected to be no-op (update as we find current terminals that support in the wild)"
               (sample-text-for-keys
                [:primary-font
                 :alt-font-1
                 :alt-font-2
                 :alt-font-3
                 :alt-font-4
                 :alt-font-5
                 :alt-font-6
                 :alt-font-7
                 :alt-font-8
                 :alt-font-9
                 :fraktur
                 :proportional
                 :disable-proportional
                 :framed
                 :encircled
                 :not-framed-nor-circled
                 :ideogram-underline
                 :ideogram-double-underline
                 :ideogram-overline
                 :ideogram-double-overline
                 :ideogram-stress
                 :ideogram-clear
                 :superscript
                 :subscript
                 :not-super-sub-script]))

(let [missed (apply dissoc sgr-codes @keys-visited)]
  (if (seq missed)
    (println "\n* Heya, your calls didn't hit: " missed)
    (println "\nAll defined sgr codes hit.")))
