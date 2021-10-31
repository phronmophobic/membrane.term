#/usr/bin/env bash

set -eou pipefail

echo "Regenerating screenshots for README"

echo "- msgcat -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-msgcat.sh \
    --height 14 \
    --out doc/images/screenshot-play-msgcat.png

echo "- deep-diff -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-deep-diff.sh \
    --width 80 --height 9 \
    --final-delay 1000 --line-delay 3000 \
    --out doc/images/screenshot-play-deep-diff.png

echo "- msgcat with color scheme -"
clj -M:membrane.term screenshot \
    --play doc/examples/play-msgcat.sh \
    --height 14 \
    --color-scheme "doc/examples/Builtin Solarized Dark.itermcolors" \
    --out doc/images/screenshot-play-msgcat-scheme.png
