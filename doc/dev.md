# Developer Notes

## Screenshot Generation

To regenerate screenshots in README, run:
```bash
script/regen-screenshots.sh
```
Changes to this script or `doc/examples/play-*` should be translated to examples in `README.md` and vice versa.

## ANSI Text Visualization

To generate a screenful of text exercising the various [SGR codes](https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters), run this [babashka](https://github.com/babashka/babashka) script:

```bash
script/ansi-text-test.clj
```

As hinted by our README, you can also use `msgcat`'s color test:
```bash
msgcat --color=test
```
This tool is available on most Linux and macOS systems.
