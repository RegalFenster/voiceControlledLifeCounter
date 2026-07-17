#!/usr/bin/env bash
# Downloads the large German Vosk speech model and places it where the app expects
# it (app/src/main/assets/model-de). Not tracked in git - a single file inside it
# (graph/HCLG.fst, ~600MB) exceeds GitHub's 100MB per-file limit. Run this once
# before building.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="$SCRIPT_DIR/../app/src/main/assets"
TARGET_DIR="$ASSETS_DIR/model-de"
MODEL_NAME="vosk-model-de-0.21"
MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"

if [ -d "$TARGET_DIR" ] && [ -f "$TARGET_DIR/uuid" ]; then
  echo "Model already present at $TARGET_DIR (delete it to re-download)."
  exit 0
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Downloading $MODEL_NAME (~1.9GB, this takes a while)..."
curl -# -L -o "$WORK_DIR/model.zip" "$MODEL_URL"

echo "Unzipping..."
unzip -q "$WORK_DIR/model.zip" -d "$WORK_DIR"

echo "Installing to $TARGET_DIR..."
mkdir -p "$ASSETS_DIR"
rm -rf "$TARGET_DIR"
mv "$WORK_DIR/$MODEL_NAME" "$TARGET_DIR"

# rescore/rnnlm are an optional accuracy-boosting rescoring pass Vosk can use if
# present, but they alone are ~2.3GB - the app works fine without them and this
# keeps the model (and build times) manageable.
rm -rf "$TARGET_DIR/rescore" "$TARGET_DIR/rnnlm"

# Vosk's StorageService needs a "uuid" file in the model to detect whether its
# on-device copy is stale; any unique string works.
if command -v uuidgen >/dev/null 2>&1; then
  uuidgen > "$TARGET_DIR/uuid"
else
  node -e "console.log(require('crypto').randomUUID())" > "$TARGET_DIR/uuid"
fi

echo "Done. Model installed at $TARGET_DIR ($(du -sh "$TARGET_DIR" | cut -f1))."
