(ns com.phronemophobic.membrane.term
  (:require [asciinema.vt :as vt]
            [asciinema.vt.screen :as screen]
            [membrane.ui :as ui]
            [membrane.skia :as skia])
  (:import com.pty4j.PtyProcess))


(defn start-pty []
  (let [cmd (into-array String ["/bin/sh" "-l"])
        ;;env (into-array String ["TERM=eterm-color"])
        pty (PtyProcess/exec cmd (System/getenv))]
    pty))

(def blank-cell [32 {}])
(defn blank-cell? [cell]
  (= cell blank-cell))

(def term-color-names [;; :white
                       :black
                       :red
                       :green
                       :yellow
                       :blue
                       :magenta
                       :cyan
                       :bright-black
                       :bright-red
                       :bright-green
                       :bright-yellow
                       :bright-blue
                       :bright-magenta
                       :bright-cyan
                       :bright-white])
(defn num->term-color-name [num]
  (let [color-name (nth term-color-names num
                   :not-found)]
    (if (= :not-found color-name)
      (do (prn "color not found: " num)
          :red)
      color-name)))

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

(defn wrap-fg-color [c elem]
  (if c
    (ui/with-color (-> c
                       num->term-color-name
                       term-color-name->color)
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
             (let [b (inc (- key (int \A) ))]
               (writec-bytes out [b]))))

         (when (not (zero? (bit-and skia/GLFW_MOD_ALT mods)))
           (when (< key 128)
             (let [key (if (not (zero? (bit-and skia/GLFW_MOD_SHIFT mods)))
                         (- key (- (int \A) (int \a)))
                         key)]
               (writec-bytes out [0x1b key]))))
         )
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

(defn run-term
  ([]
   (run-term {}))
  ([{:keys [width height]
     :as opts
     :or {width 90
          height 30}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})]
     (swap! term-state assoc :pty (start-pty))
     (future
       (while true
         (let [input (.read (.getInputStream (:pty @term-state)))]
           ;; (prn "input: " input)
           (swap! term-state update :vt vt/feed-one input))))

     (skia/run ;; -sync
       (fn []
         (let [{:keys [pty vt]} @term-state]
           (term-events pty
                        (term-view (:vt @term-state)))))
       {:window-start-width (* width cell-width)
        :window-start-height (* height cell-height)})
     
     #_(let [pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty)))))

  )


(defn run-script
  ([{:keys [path width height]
     :or {width 90
          height 30}}]
   (let [term-state (atom {:vt (vt/make-vt width height)})]
     (swap! term-state assoc :pty (start-pty))
     (future
       (while true
         (let [input (.read (.getInputStream (:pty @term-state)))]
           ;; (prn "input: " input)
           (swap! term-state update :vt vt/feed-one input))))

     (doseq [line (clojure.string/split-lines (slurp path))]
       (send-input (:pty @term-state) line)
       (send-input (:pty @term-state) "\n")
       (Thread/sleep 1e3))

     (Thread/sleep 10e3)
     (skia/draw-to-image! "terminal.png"
                          (ui/padding 5
                                      (term-view (:vt @term-state))))
     
     (let [pty (:pty @term-state)]
       (.close (.getInputStream pty))
       (.close (.getOutputStream pty))))
   )
  )

