#!/usr/bin/env python3
"""Bounded, local SigLIP2 metadata pilot. Never edits source images or approves training data."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import random
from datetime import datetime, timezone
from pathlib import Path

from triage_archaeological_images import MODEL_ID, MAX_PIXELS, RASTER_SUFFIXES

PROMPT_VERSION = "multiscript-appearance-v1"
MAX_BYTES = 50 * 1024 * 1024
PROMPTS = {
    "image_type": {
        "photograph": "A camera photograph of a real physical object or surface.",
        "document_scan": "A scanned page of a physical document.",
        "digital_rendering": "A computer-generated glyph or digital graphic on a plain background.",
        "illustration": "A drawn illustration or tracing of a written character.",
        "unknown": "An image whose origin or reproduction technique is unclear.",
    },
    "material_appearance": {
        "stone": "Writing visible on a stone surface.",
        "paper": "Writing visible on a sheet of paper.",
        "metal": "Writing visible on a metal surface.",
        "wood": "Writing visible on a wooden surface.",
        "clay": "Writing visible on a clay or ceramic surface.",
        "textile": "Writing visible on a textile surface.",
        "glass": "Writing visible on a glass surface.",
        "unknown": "An image where the physical writing surface cannot be determined.",
    },
    "writing_technique_appearance": {
        "handwritten": "Writing with visible handwritten pen or brush strokes.",
        "printed": "Writing reproduced by printing.",
        "carved_or_engraved": "Writing visibly cut into a surface.",
        "painted": "Writing painted onto a surface.",
        "embossed": "Writing raised above a surface by embossing.",
        "digital": "A digitally rendered written character.",
        "unknown": "Writing whose production technique cannot be determined.",
    },
    "script_appearance": {
        "Latin": "Writing in the Latin alphabet.",
        "Greek": "Writing in the Greek alphabet.",
        "Cyrillic": "Writing in the Cyrillic alphabet.",
        "Arabic": "Writing in the Arabic script.",
        "Hebrew": "Writing in the Hebrew alphabet.",
        "Bengali": "Writing in the Bengali script.",
        "Devanagari": "Writing in the Devanagari script.",
        "Han": "Writing using Han characters.",
        "Hiragana": "Writing in Japanese hiragana.",
        "Katakana": "Writing in Japanese katakana.",
        "Hangul": "Writing in Korean Hangul.",
        "Coptic": "Writing in the Coptic alphabet.",
        "Egyptian_hieroglyphs": "Writing in Egyptian hieroglyphs.",
        "mixed_or_other": "Writing using multiple scripts or a different script.",
        "unknown": "An image with no identifiable writing system.",
    },
    "condition_appearance": {
        "intact": "Writing that appears clear and physically intact.",
        "weathered": "Writing on a visibly weathered surface.",
        "fragmented": "Writing on a visibly broken or fragmented object.",
        "faded": "Writing with visibly faded marks.",
        "unknown": "An image where physical condition cannot be assessed.",
    },
    "image_quality": {
        "clear": "A clear sharp image of writing.",
        "blurred": "An out of focus or motion blurred image of writing.",
        "low_contrast": "Writing with very low visual contrast.",
        "obscured": "Writing partly hidden by shadows or objects.",
        "unknown": "An image whose quality cannot be assessed.",
    },
}


def assess(scores, minimum_score, minimum_margin):
    if len(scores) < 2 or any(not math.isfinite(v) or not 0 <= v <= 1 for v in scores.values()):
        raise ValueError("Invalid candidate scores")
    ranking = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    label, score = ranking[0]
    margin = score - ranking[1][1]
    candidate = label if label != "unknown" and score >= minimum_score and margin >= minimum_margin else None
    return {"candidate": candidate, "bestLabel": label, "bestScore": score, "margin": margin,
            "scores": dict(ranking), "status": "UNVERIFIED" if candidate else "ABSTAINED",
            "origin": "AI_INFERRED"}


def select_images(directory, limit, seed):
    if not directory.is_dir():
        raise ValueError(f"Missing image directory: {directory}")
    root = directory.resolve()
    files = sorted(p for p in root.rglob("*") if p.is_file() and not p.is_symlink()
                   and p.suffix.lower() in RASTER_SUFFIXES and p.resolve().is_relative_to(root))
    return sorted(random.Random(seed).sample(files, min(limit, len(files))))


def open_verified_image(path):
    from PIL import Image, ImageOps
    with path.open("rb") as stream:
        data = stream.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("Image exceeds pilot byte limit")
    digest = hashlib.sha256(data).hexdigest()
    with Image.open(io.BytesIO(data)) as original:
        if original.width * original.height > MAX_PIXELS:
            raise ValueError("Image exceeds pilot pixel limit")
        image = ImageOps.exif_transpose(original).convert("RGB")
    return digest, image


class Siglip2MetadataEnricher:
    def __init__(self, revision):
        import torch
        from transformers import AutoProcessor, Siglip2Model
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA unavailable: run in the existing GPU environment")
        self.torch = torch
        self.processor = AutoProcessor.from_pretrained(MODEL_ID, revision=revision)
        self.model = Siglip2Model.from_pretrained(MODEL_ID, revision=revision, torch_dtype=torch.float16).to("cuda").eval()
        self.revision = getattr(self.model.config, "_commit_hash", None) or revision

    def predict(self, image, minimum_score, minimum_margin):
        flattened = [(category, label, prompt) for category, labels in PROMPTS.items() for label, prompt in labels.items()]
        inputs = self.processor(text=[row[2] for row in flattened], images=image,
                                padding="max_length", truncation=True, max_length=64, return_tensors="pt").to("cuda")
        with self.torch.inference_mode():
            scores = self.torch.sigmoid(self.model(**inputs).logits_per_image[0]).float().cpu().tolist()
        if len(scores) != len(flattened):
            raise RuntimeError("Unexpected model score count")
        grouped = {category: {} for category in PROMPTS}
        for (category, label, _), score in zip(flattened, scores):
            grouped[category][label] = score
        predictions = {category: assess(values, minimum_score, minimum_margin) for category, values in grouped.items()}
        if predictions["image_type"]["candidate"] != "photograph":
            for category in ("material_appearance", "condition_appearance"):
                predictions[category].update(candidate=None, status="ABSTAINED", reason="Physical appearance requires a photograph in this pilot")
        return predictions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-dir", type=Path, required=True)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--output", type=Path, required=True, help="New JSONL file; never overwrites an existing report")
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--minimum-score", type=float, default=0.5)
    parser.add_argument("--minimum-margin", type=float, default=0.06)
    parser.add_argument("--revision", required=True, help="Hugging Face model commit SHA (40 hexadecimal characters)")
    args = parser.parse_args()
    if not 1 <= args.limit <= 1000 or not 0 <= args.minimum_score <= 1 or not 0 <= args.minimum_margin <= 1:
        parser.error("Limit must be 1–1000 and thresholds 0–1")
    if len(args.revision) != 40 or any(c not in "0123456789abcdef" for c in args.revision.lower()):
        parser.error("--revision must be a full model commit SHA")
    if args.output.suffix.lower() != ".jsonl" or args.output.exists():
        parser.error("--output must be a new JSONL file")
    selected = select_images(args.image_dir, args.limit, args.seed)
    if not selected:
        parser.error("No supported raster images found")
    enricher = Siglip2MetadataEnricher(args.revision)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    failures = 0
    with args.output.open("x", encoding="utf-8") as stream:
        for index, path in enumerate(selected, 1):
            row = {"sourceId": args.source_id, "imagePath": path.relative_to(args.image_dir.resolve()).as_posix(),
                   "model": MODEL_ID, "modelRevision": enricher.revision, "promptVersion": PROMPT_VERSION,
                   "prompts": PROMPTS, "preprocessing": "EXIF_TRANSPOSE_RGB", "scoreType": "UNCALIBRATED_SIGMOID",
                   "minimumScore": args.minimum_score, "minimumMargin": args.minimum_margin, "sampleSeed": args.seed,
                   "processedAt": datetime.now(timezone.utc).isoformat(), "trainingDataApproved": False}
            try:
                digest, image = open_verified_image(path)
                row["sha256"] = digest
                row["predictions"] = enricher.predict(image, args.minimum_score, args.minimum_margin)
                row["status"] = "PROPOSED"
            except Exception as error:
                failures += 1
                row.update(status="FAILED", errorType=type(error).__name__, error=str(error)[:250])
            stream.write(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n")
            stream.flush()
            print(f"{index}/{len(selected)} {row['status']}", flush=True)
    print(f"Report: {args.output}; processed={len(selected)}, failed={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
