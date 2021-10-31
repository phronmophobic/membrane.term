(ns com.phronemophobic.membrane.term
  (:require [asciinema.vt :as vt]
            [clojure.string :as string]
            [com.phronemophobic.membrane.term.color-scheme :as color-scheme]
            [membrane.ui :as ui]
            [membrane.skia :as skia])
  (:import [com.pty4j PtyProcess WinSize]))


(defn start-pty []
  (let [cmd (into-array String ["/bin/bash" "-l"])
        pty (PtyProcess/exec ^"[Ljava.lang.String;" cmd
                             ^java.util.Map (merge (into {} (System/getenv))
                                                   {"TERM" "xterm-256color"}))]
    pty))

(def blank-cell [32 {}])
(defn blank-cell? [cell]
  (= cell blank-cell))

(def default-color-scheme
  {:white           [1     1     1]
   :black           [0     0     0]
   :red             [0.76  0.21  0.13]
   :green           [0.14  0.74  0.14]
   :yellow          [0.68  0.68  0.15]
   :blue            [0.29  0.18  0.88]
   :magenta         [0.83  0.22  0.83]
   :cyan            [0.20  0.73  0.78]
   :bright-black    [0.46  0.46  0.46]
   :bright-red      [0.91  0.28  0.34]
   :bright-green    [0.09  0.78  0.05]
   :bright-yellow   [0.98  0.95  0.65]
   :bright-blue     [0.23  0.47  1]
   :bright-magenta  [0.71  0     0.62]
   :bright-cyan     [0.38  0.84  0.84]
   :bright-white    [0.95  0.95  0.95]
   :cursor          [0.57  0.57  0.57]
   :cursor-text     [0     0     0]
   :background      [1     1     1]
   :foreground      [0     0     0]})

(defn vt-color->term-color
  [color-scheme vt-color]
  (if (vector? vt-color)
    (let [[r g b] vt-color]
      [(/ r 255.0)
       (/ g 255.0)
       (/ b 255.0)])
    (case vt-color
      0 (color-scheme :black)
      1 (color-scheme :red)
      2 (color-scheme :green)
      3 (color-scheme :yellow)
      4 (color-scheme :blue)
      5 (color-scheme :magenta)
      6 (color-scheme :cyan)
      7 (color-scheme :white)

      8  (color-scheme :bright-black)
      9  (color-scheme :bright-red)
      10 (color-scheme :bright-green)
      11 (color-scheme :bright-yellow)
      12 (color-scheme :bright-blue)
      13 (color-scheme :bright-magenta)
      14 (color-scheme :bright-cyan)
      15 (color-scheme :bright-white)

    ;; else
      (cond

        (and (>= vt-color 16)
             (<= vt-color 231))
        (let [num (- vt-color 16)
              v [0x00, 0x5f, 0x87, 0xaf, 0xd7, 0xff]
              r (nth v (int (mod (/ num 36.0) 6)))
              g (nth v (int (mod (/ num 6.0) 6)))
              b (nth v (int (mod num 6.0)))]
          [(/ r 255.0)
           (/ g 255.0)
           (/ b 255.0)])

        (and (>= vt-color 232)
             (<= vt-color 255))
        (let [gray (/ (+ 8 (* 10 (- vt-color 232))) 255.0)]
          [gray gray gray])

        :else
        (do (println "color not found: " vt-color)
            (color-scheme :red))))))



(defn wrap-color [color-scheme c elem]
  (if c
    (ui/with-color (vt-color->term-color color-scheme c)
      elem)
    elem))

(def term-font
  (cond
    (skia/font-exists? (ui/font "Menlo" 12))
    (ui/font "Menlo" 12)

    :else (ui/font "monospace" 12)))

(def font-metrics (skia/skia-font-metrics term-font))
(def cell-width (skia/skia-advance-x term-font " "))
(def cell-height (skia/skia-line-height term-font))
(def bg-offset (:Descent font-metrics))

(defn- character [c {:keys [bold italic]}]
  (ui/label (Character/toString (char c))
            (assoc term-font
                   :weight (if bold
                             :bold
                             :normal)
                   :slant (if italic
                            :italic
                            :upright))))

(defn term-line [color-scheme line]
  (into []
        (comp
         (map-indexed vector)
         (remove (fn [[_ cell]]
                   (blank-cell? cell)))
         (map
          (fn [[i [c attrs]]]
            (let [foreground (ui/with-color (if-let [vt-color (:fg attrs)]
                                              (vt-color->term-color color-scheme vt-color)
                                              (:foreground color-scheme))
                               (character c attrs))
                  background (when-let [bg (:bg attrs)]
                               (ui/translate 0 bg-offset
                                             (wrap-color color-scheme bg
                                                         (ui/rectangle (inc cell-width)
                                                                       (inc cell-height)))))]

              (ui/translate
               (* cell-width i) 0
               (if background
                 [background foreground]
                 foreground))))))
        line))

(def term-line-memo (memoize term-line))
(def window-padding-height 8)

(defn term-view [color-scheme vt]
  (let [screen (:screen vt)
        cursor (let [{:keys [x y visible]} (:cursor screen)]
                 (when visible
                   (ui/translate
                    (* cell-width x) (* cell-height y)
                    [(ui/translate 0 bg-offset
                      (ui/with-color (:cursor color-scheme)
                        (ui/rectangle (inc cell-width) (inc cell-height))))
                     (ui/with-color  (:cursor-text color-scheme)
                       (apply character (-> vt :screen :lines (nth y) (nth x))))])))]
    (ui/no-events
     (conj [(ui/with-color (:background color-scheme)
              (ui/rectangle (* cell-width (:width screen))
                            (+ window-padding-height (* cell-height (:height screen)))))]
           (into []
                 (comp (map-indexed
                        (fn [i line]
                          (ui/translate
                           0 (* i cell-height)
                           (ui/->Cached
                            (term-line-memo color-scheme line))))))
                 (-> vt :screen :lines))
           cursor))))

(defn writec-bytes [out bytes]
  (.write ^java.io.OutputStream out (byte-array bytes)))

(defn send-input [pty s]
  (let [out (.getOutputStream ^PtyProcess pty)]
    (writec-bytes out (.getBytes ^String s))))

(def meta-shift-map
  {
   \` \~
   \1 \!
   \2 \@
   \3 \#
   \4 \$
   \5 \%
   \6 \^
   \7 \&
   \8 \*
   \9 \(
   \0 \)
   \- \_
   \= \+

   \[ \{
   \] \}
   \\ \|

   \; \:
   \' \"

   \, \<
   \. \>
   \/ \?})

(defn term-events [pty view]
  (let [out (.getOutputStream ^PtyProcess pty)]
    (ui/on
     :key-event
     (fn [key _scancode action mods]

       (when (#{:press :repeat} action)
         (case (int key)
           ;; backspace
           259 (writec-bytes out [0x7f])

           ;; escape
           256 (writec-bytes out [0x1b])

           ;; tab
           258 (writec-bytes out [(int \tab)])


           262 ;; right
           (writec-bytes out (map int [033 \[ \C]))

           #_left 263
           (writec-bytes out (map int [033 \[ \D]))

           264 (writec-bytes out (map int [033 \[ \B]))
           ;; down

           ;; up
           265
           (writec-bytes out (map int [0x1b \[ \A]))

           ;; default
           nil
           )


         (when (not (zero? (bit-and skia/GLFW_MOD_CONTROL mods)))
           (when (< key 128)
             (case (char key)
               (\A \B \C \D \E \F \G \H \I \J \K \L \M \N \O \P \Q \R \S \T \U \V \W \X \Y \Z)
               (let [b (inc (- key (int \A) ))]
                 (writec-bytes out [b]))

               \space
               (let [b (inc (- (int (char \@)) (int \A) ))]
                 (writec-bytes out [b]))

               \-
               (let [b (inc (- (int \_) (int \A)))]
                 (writec-bytes out [b]))

               nil)))

         (when (not (zero? (bit-and skia/GLFW_MOD_ALT mods)))
           (when (< key 128)
             (case (char key)

               (\A \B \C \D \E \F \G \H \I \J \K \L \M \N \O \P \Q \R \S \T \U \V \W \X \Y \Z)
               (let [key (if (zero? (bit-and skia/GLFW_MOD_SHIFT mods))
                           (- key (- (int \A) (int \a)))
                           key)]
                 (writec-bytes out [0x1b key]))

               ;; else
               (let [key (if (not (zero? (bit-and skia/GLFW_MOD_SHIFT mods)))
                           (when-let [c (get meta-shift-map (char key))]
                             (int c))
                           key)]
                 (when key
                   (writec-bytes out [0x1b key])))))))
       nil
       )
     :key-press
     (fn [s]
       (when-let [s (if (keyword? s)
                      (case s
                        :enter "\r"

                        ;; default
                        nil)
                      s)]
         (let [bts (.getBytes ^String s)]
           (when (pos? (first bts))
             (writec-bytes out bts)
             )))

       nil)
     view)))

(defn run-pty-process [width height term-state]
  (let [^PtyProcess
        pty (doto ^PtyProcess (start-pty)
              (.setWinSize (WinSize. width height)))]
    (future
      (try
        (loop []
          (let [input (.read (.getInputStream pty))]
            (when (not= -1 input)
              (swap! term-state update :vt vt/feed-one input)
              (recur))))
        (catch Exception e
          (prn e))))
    pty))

(defn- load-color-scheme [source]
  (if source
    (color-scheme/load-scheme source)
    default-color-scheme))

(defn run-term
  ([]
   (run-term {}))
  ([{:keys [width height color-scheme]
     :as _opts
     :or {width 90
          height 30}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})
         color-scheme (load-color-scheme color-scheme)]
     (swap! term-state assoc
            :pty (run-pty-process width height term-state))
     (skia/run-sync
      (fn []
        (let [{:keys [pty vt]} @term-state]
          (term-events pty
                       (term-view color-scheme vt))))
      {:window-start-width (* width cell-width)
       :window-start-height (+ window-padding-height (* height cell-height))})

     (let [^PtyProcess pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty))))))

(comment
  (run-term)
  (run-term {:color-scheme "https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/master/schemes/Builtin%20Solarized%20Dark.itermcolors"})
  (run-term {:color-scheme "https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/master/schemes/Ocean.itermcolors"})

  (run-term {:color-scheme "https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/master/schemes/Belafonte%20Day.itermcolors"})
  )

(defn screenshot
  ([{:keys [play width height out line-delay final-delay color-scheme]
     :as _opts
     :or {width 90
          height 30
          line-delay 1e3
          final-delay 10e3
          out "terminal.png"}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})
         color-scheme (load-color-scheme color-scheme)]
     (swap! term-state assoc
            :pty (run-pty-process width height term-state))
     (doseq [line (string/split-lines (slurp play))]
       (send-input (:pty @term-state) line)
       (send-input (:pty @term-state) "\n")
       (Thread/sleep line-delay))

     (Thread/sleep final-delay)
     (skia/draw-to-image! out
                          (ui/fill-bordered (:background color-scheme) 5
                                            (term-view color-scheme (:vt @term-state))))
     (println (str "Wrote screenshot to " out "."))

     (let [^PtyProcess pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty))))))
