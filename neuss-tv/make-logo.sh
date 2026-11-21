#!/bin/bash
# Turns one logo file into the app's launcher icon and TV banner, then the app is rebuilt with them.
# Usage: ./make-logo.sh ~/path/to/logo.png        (PNG, JPG or WebP; a square logo works best)
set -e
SRC="$1"
[ -f "$SRC" ] || { echo "usage: $0 <logo file>"; exit 1; }
cd "$(dirname "$0")"
OUT=app/src/main/res/drawable-xhdpi
BG=white            # the logo artwork is on white; a dark tile would show it as a white box
mkdir -p "$OUT"

# Launcher icon: 320x320, logo fitted with a margin.
ffmpeg -v error -y -sws_flags lanczos+accurate_rnd -i "$SRC" \
  -vf "scale=256:256:force_original_aspect_ratio=decrease,pad=320:320:(ow-iw)/2:(oh-ih)/2:color=$BG" \
  "$OUT/icon.png"

# TV banner: 320x180, the shape Android TV asks for.
ffmpeg -v error -y -sws_flags lanczos+accurate_rnd -i "$SRC" \
  -vf "scale=150:120:force_original_aspect_ratio=decrease,pad=320:180:(ow-iw)/2:(oh-ih)/2:color=$BG" \
  "$OUT/banner.png"

# The hand-drawn placeholders are no longer needed.
rm -f app/src/main/res/drawable/icon.xml app/src/main/res/drawable/banner.xml

echo "wrote $OUT/icon.png and $OUT/banner.png"
