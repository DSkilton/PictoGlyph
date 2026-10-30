#!/usr/bin/env bash
# One-shot, resumable-in-stages manifest + visual triage pilot; never touches originals.
set -Eeuo pipefail
ROOT=/mnt/i/PictoGlyph/data/research-datasets
REPO=/mnt/e/ml/projects/pictoglyph
ACQUISITION="$REPO/pictoglyphml/datasets/acquisition"
TRIAGE="$REPO/pictoglyphml/experiments/triage_archaeological_images.py"
OUT="$ROOT/manifests/multiscript-v1"
RESULTS="$ROOT/triage-results"
mkdir -p "$OUT" "$RESULTS"
exec 9>/tmp/pictoglyph-multiscript-pilot.lock
if ! flock -n 9; then
  echo 'Another PictoGlyph large pilot is running. Exiting.'
  exit 1
fi
log() { printf '%s | %s\n' "$(date -Is)" "$*"; }
trap 'status=$?; log "Pilot exited (status=$status)"; exit "$status"' EXIT

log 'Stage 1: full Indus, Cretan and Linear A metadata/image manifest'
python -u "$ACQUISITION/build_multiscript_manifest.py" \
  --root "$ROOT" --output "$OUT"

log 'Stage 2: SigLIP2 visual triage, reproducible sample of 1000 per image-bearing source'
curl -fsS http://127.0.0.1:8001/health >/dev/null
# The container's research datasets and repository mounts are read-only.
docker exec pictoglyph_ml /opt/venv/bin/python \
  /workspace/pictoglyph/pictoglyphml/experiments/triage_archaeological_images.py \
  --source indus-lipi --source cretan-hieroglyphic --limit 1000 --seed 17 \
  --output /tmp/pictoglyph_image_triage_large.csv \
  --contact-sheet /tmp/pictoglyph_image_triage_large_review.jpg

log 'Stage 3: persist visual screening results on I: (outside Docker)'
docker cp pictoglyph_ml:/tmp/pictoglyph_image_triage_large.csv "$RESULTS/"
docker cp pictoglyph_ml:/tmp/pictoglyph_image_triage_large.summary.json "$RESULTS/"
docker cp pictoglyph_ml:/tmp/pictoglyph_image_triage_large_review.jpg "$RESULTS/"

log 'Stage 4: merge triage scores into image manifest'
python -u "$ACQUISITION/build_multiscript_manifest.py" \
  --root "$ROOT" --output "$OUT" \
  --triage-csv "$RESULTS/pictoglyph_image_triage_large.csv"
log 'All stages finished successfully'
