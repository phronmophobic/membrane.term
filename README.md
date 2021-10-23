# membrane.term

A simple terminal emulator in clojure.

## Rationale

I thought it would be fun. There's not much code. Most of the work is done by [asciinema/vt](https://github.com/asciinema/vt). `membrane.term` is just small UI on top. If you needed to programmatically control, inspect, drive, or manipulate a terminal, then either `asciinema/vt` or `membrane.term` might be a good fit. It might also be a good place to start if you wanted to learn more about how terminal emulators work.

## Dependency

```
{:git/sha "65238c3bf61ed3392f00f41df3329d67eb5b92ec"
 :git/url "https://github.com/phronmophobic/membrane.term"}
```

## Usage

Open a terminal window.

    clojure -X com.phronemophobic.membrane.term/run-term :width 90 :height 30

Run a script in a headless terminal and write an image to terminal.png.

    clojure -X com.phronemophobic.membrane.term/run-script :path '"script.sh"'
    
The script is passed to the terminal character by character. The script also accepts the following optional arguments:

`width`: number of columns for the terminal. default 90
`height`: number of rows for the terminal. default 30
`out`: filename of the image to write to. default "terminal.png"
`line-delay`: If you type a command like `lein repl`, the underlying program might not be ready to accept input immediately. You can specify a delay in ms to wait after each line is sent to the terminal. default 1000.
`final-delay`: For the same reasons as `line-delay`, there is a final delay before writing the view of the terminal. default 10000.

Example:

    clojure -X com.phronemophobic.membrane.term/run-script :path '"script.sh"' :width 120 :height 30 :final-delay 30e3 :line-delay 0 :out '"foo.jpg"'

## License

Copyright © 2021 Adrian Smith

Distributed under the Eclipse Public License version 1.0.
