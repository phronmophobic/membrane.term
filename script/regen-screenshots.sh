#/usr/bin/env bash

set -eou pipefail

echo "Regenerating screenshots for README"

echo "- msgcat -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-msgcat.sh \
    --height 14 \
    --out doc/images/screenshot-msgcat.png

echo "- deep-diff -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-deep-diff.sh \
    --width 80 --height 9 \
    --final-delay 1000 --line-delay 3000 \
    --out doc/images/screenshot-deep-diff.png

echo "- msgcat with color scheme -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-msgcat.sh \
    --height 14 \
    --color-scheme "doc/examples/Builtin Solarized Dark.itermcolors" \
    --out doc/images/screenshot-msgcat-scheme.png

echo "- msgcat with font -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-msgcat.sh \
    --height 14 \
    --font-family "NovaMono" \
    --font-size 16 \
    --out doc/images/screenshot-msgcat-font.png
