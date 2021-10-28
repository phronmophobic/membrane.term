# membrane.term

A simple terminal emulator in clojure.

## Rationale

I thought it would be fun. There's not much code. All of the ANSI parsing work is done by [asciinema/vt](https://github.com/asciinema/vt). `membrane.term` is just small UI on top.

Some reasons to use **membrane.term**:
- If you need to programmatically control, inspect, drive, or manipulate a terminal
- If you want to learn more about how terminal emulators work.
- If you'd like to embed a terminal somewhere. There's not really a guide for embedding, but if you file an issue, I can provide some tips.

## Dependency

```
{:git/sha "d09d563ee9c1677ee9a2ecd186f84bb6214e94f7"
 :git/url "https://github.com/phronmophobic/membrane.term"}
```

## Usage

### Run a GUI terminal

Open a terminal window.

    clojure -X com.phronemophobic.membrane.term/run-term :width 90 :height 30

![run-term-screenshot](terminal.gif?raw=true)

If addition to `:width` and `:height`, the `run-term` function also accepts [`:color-scheme`](#color-schemes). 
### Run a headless terminal and take a screenshot

Run a script in a headless terminal and write an image to terminal.png.

    clojure -X com.phronemophobic.membrane.term/run-script :path '"script.sh"'
    
The script is passed to the terminal character by character. The `run-script` function also accepts the following optional arguments:

`:width`: number of columns for the terminal. default 90  
`:height`: number of rows for the terminal. default 30  
`:out`: filename of the image to write to. default "terminal.png"  
`:line-delay`: If you type a command like `lein repl`, the underlying program might not be ready to accept input immediately. You can specify a delay in ms to wait after each line is sent to the terminal. default 1000.  
`:final-delay`: For the same reasons as `line-delay`, there is a final delay before writing the view of the terminal. default 10000.  
[`:color-scheme`](#color-schemes): choose a different color scheme.

Example:

    clojure -X com.phronemophobic.membrane.term/run-script :path '"script.sh"' :width 120 :height 30 :final-delay 30e3 :line-delay 0 :out '"foo.jpg"'

## Color Schemes

There are boatloads of terminal color schemes available at [iTerm2-Color-Schemes](https://github.com/mbadolato/iTerm2-Color-Schemes#screenshots).
Review what you might like via the screenshots, then find the corresponding [`.itermcolors` file under the schemes directory](https://github.com/mbadolato/iTerm2-Color-Schemes/tree/master/schemes).

Use the `:color-scheme` option to specify an `.itermcolors` file, either from a copy you have downloaded:

```
clojure -X com.phronemophobic.membrane.term/run-term :width 90 :height 30 \
  :color-scheme '"Builtin Solarized Dark.itermcolors"'
```

...or directly from the raw GitHub URL:

```
clojure -X com.phronemophobic.membrane.term/run-term :width 90 :height 30 \
  :color-scheme '"https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/master/schemes/Builtin%20Solarized%20Dark.itermcolors"'
```

:point_right: Don't forget that the Clojure CLI -X syntax for strings requires `'"special quoting"'`.
## License

Copyright © 2021 Adrian Smith

Distributed under the Eclipse Public License version 1.0.
