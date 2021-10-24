(ns com.phronemophobic.membrane.term
  (:require [asciinema.vt :as vt]
            [asciinema.vt.screen :as screen]
            [membrane.ui :as ui]
            [membrane.skia :as skia])
  (:import [com.pty4j PtyProcess WinSize]))


(defn start-pty []
  (let [cmd (into-array String ["/bin/bash" "-l"])
        pty (PtyProcess/exec cmd
                             (merge (into {} (System/getenv))
                                    {"TERM" "xterm-256color"}))]
    pty))

(def blank-cell [32 {}])
(defn blank-cell? [cell]
  (= cell blank-cell))

(def term-color-name->color
  {:white           [1     1     1   ]
   :black           [0     0     0   ]
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
   :bright-blue     [0.23  0.47  1   ]
   :bright-magenta  [0.71  0     0.62]
   :bright-cyan     [0.38  0.84  0.84]
   :bright-white    [0.95  0.95  0.95]})


(defn num->term-color [num]
  (case num
    0 (term-color-name->color :black)
    1 (term-color-name->color :red)
    2 (term-color-name->color :green)
    3 (term-color-name->color :yellow)
    4 (term-color-name->color :blue)
    5 (term-color-name->color :magenta)
    6 (term-color-name->color :cyan)
    7 (term-color-name->color :white)

    8  (term-color-name->color :bright-black)
    9  (term-color-name->color :bright-red)
    10 (term-color-name->color :bright-green)
    11 (term-color-name->color :bright-yellow)
    12 (term-color-name->color :bright-blue)
    13 (term-color-name->color :bright-magenta)
    14 (term-color-name->color :bright-cyan)
    15 (term-color-name->color :bright-white)

    ;; else
    (cond

      (and (>= num 16)
           (<= num 231))
      (let [num (- num 16)
            v [0x00, 0x5f, 0x87, 0xaf, 0xd7, 0xff]
            r (nth v (int (mod (/ num 36.0) 6)))
            g (nth v (int (mod (/ num 6.0) 6)))
            b (nth v (int (mod num 6.0)))]
        [(/ r 255.0)
         (/ g 255.0)
         (/ b 255.0)])

      (and (>= num 232)
           (<= num 255))
      (let [gray (/ (+ 8 (* 10 (- num 232))) 255.0)]
        [gray gray gray])

      :else
      (do (prn "color not found: " num)
          :red))
    ))

(defn wrap-fg-color [c elem]
  (if c
    (ui/with-color (num->term-color c)
      elem)
    elem))



(def term-font
  (cond
    (skia/font-exists? (ui/font "Menlo" 12))
    (ui/font "Menlo" 12)

    :else (ui/font "monospace" 12)))

(def font-metrics (skia/skia-font-metrics term-font))
(def cell-width (#'skia/skia-advance-x term-font " "))
(def cell-height (skia/skia-line-height term-font))
(def bg-offset (:Descent font-metrics))

(defn term-line [line]
  (into []
        (comp
         (map-indexed vector)
         (remove (fn [[_ cell]]
                   (blank-cell? cell)))
         (map
          (fn [[i [c attrs]]]
            (let [foreground (wrap-fg-color (:fg attrs)
                                            (ui/label (Character/toString (char c))
                                                      term-font))
                  background (when-let [bg (:bg attrs)]
                               (ui/translate 0 bg-offset
                                             (wrap-fg-color bg
                                                            (ui/rectangle (inc cell-width)
                                                                          (inc cell-height)))))]
              
              (ui/translate
               (* cell-width i) 0
               (if background
                 [background foreground]
                 foreground))))))
        line))
(def term-line-memo (memoize term-line))


(def cursor-color [0.5725490196078431
                   0.5725490196078431
                   0.5725490196078431
                   0.4])
(defn term-view [vt]
  (ui/no-events
   (into [(let [{:keys [x y visible]} (-> vt :screen :cursor)]
            (when visible
              (ui/translate (* x cell-width)
                            (+ (* y cell-height)
                               bg-offset)
                            (ui/with-color cursor-color
                              (ui/rectangle (inc cell-width) (inc cell-height))))))]
         (comp (map-indexed
                (fn [i line]
                  (ui/translate
                   0 (* i cell-height)
                   (ui/->Cached
                    (term-line-memo line))))))
         (-> vt :screen :lines))))

(comment
  

  (.close (.getInputStream (:pty @pty-state)))
  ,)

(defn writec-bytes [out bytes]
  (doseq [b bytes]
    (.write out (int b))))

(defn send-input [pty s]
  (let [out (.getOutputStream pty)]
    (writec-bytes out (.getBytes s))))

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
  (let [out (.getOutputStream pty)]
    (ui/on 
     :key-event
     (fn [key scancode action mods]

       (when (#{:press :repeat} action)
         (case key
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
         (let [bts (.getBytes s)]
           (when (pos? (first bts))
             (writec-bytes out bts)
             )))
       
       nil)
     view)))

(defn run-pty-process [width height term-state]
  (let [pty (doto (start-pty)
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

(defn run-term
  ([]
   (run-term {}))
  ([{:keys [width height]
     :as opts
     :or {width 90
          height 30}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})]
     (swap! term-state assoc
            :pty (run-pty-process width height term-state))
     (skia/run-sync
       (fn []
         (let [{:keys [pty vt]} @term-state]
           (term-events pty
                        (term-view (:vt @term-state)))))
       {:window-start-width (* width cell-width)
        :window-start-height (+ 8 (* height cell-height))})
     
     (let [pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty))))))

(defn run-script
  ([{:keys [path width height out line-delay final-delay]
     :or {width 90
          height 30
          line-delay 1e3
          final-delay 10e3
          out "terminal.png"}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})]
     (swap! term-state assoc
            :pty (run-pty-process width height term-state))
     (doseq [line (clojure.string/split-lines (slurp path))]
       (send-input (:pty @term-state) line)
       (send-input (:pty @term-state) "\n")
       (Thread/sleep line-delay))

     (Thread/sleep final-delay)
     (skia/draw-to-image! out
                          (ui/padding 5
                                      (term-view (:vt @term-state))))
     (println (str "Wrote to " out ".") )
     
     (let [pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty))))
   )
  )

