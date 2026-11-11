#!/usr/bin/env python3
"""PG-ING-10: import archaeological research manifests without inventing sign labels.

Reads build_multiscript_manifest.py CSV output, preserves unlinked images as
independent records, adds checksums/triage/crop proposals, and optionally POSTs
idempotent records to the PG-ING-08 Spring API. Defaults to local preview only.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
TRIAGE_SCRIPT = HERE.parent.parent / "experiments" / "triage_archaeological_images.py"
MANIFEST_SCRIPT = HERE / "build_multiscript_manifest.py"
CATALOGUE = HERE / "sources.json"


def read_csv(path: Path) -> list[dict]:
    if not path.is_file():
        raise FileNotFoundError(path)
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def within_root(raw: str, root: Path, max_bytes: int) -> Path:
    path = Path(raw).resolve(strict=True)
    if not path.is_file() or not path.is_relative_to(root.resolve(strict=True)):
        raise ValueError(f"Image path must be a regular file within research root: {raw}")
    if path.stat().st_size > max_bytes:
        raise ValueError(f"Image exceeds size cap: {path}")
    return path


def context(row: dict) -> dict:
    return {key: row.get(key, "") for key in (
        "script", "inscription_id", "site", "region", "period", "material", "object_type",
        "transcription", "transcription_status", "source_record_reference"
    )}


def build_payloads(manifest: Path, root: Path, max_image_mb: int = 100,
                   triage_csv: Path | None = None) -> tuple[list[dict], dict]:
    inscriptions = read_csv(manifest / "inscriptions.csv")
    images = read_csv(manifest / "images.csv")
    crop_path = manifest / "provisional_crops.csv"
    crops = read_csv(crop_path) if crop_path.is_file() else []
    by_path = defaultdict(list)
    for crop in crops:
        by_path[crop.get("image_path", "")].append(crop)

    scores = {row["image_path"]: row for row in read_csv(triage_csv)} if triage_csv else {}
    catalogue = {entry["id"]: entry for entry in json.loads(CATALOGUE.read_text(encoding="utf-8"))["sources"]}
    records = {}
    for row in inscriptions:
        source, inscription_id = row["source_id"], row.get("inscription_id", "")
        if not inscription_id:
            continue
        key = (source, "inscription:" + inscription_id)
        acquisition = root / "scripts" / source / "acquisition.json"
        prov = json.loads(acquisition.read_text(encoding="utf-8")) if acquisition.exists() else {}
        records[key] = {
            "sourceId": source, "recordKey": key[1], "recordType": "INSCRIPTION",
            "sourceUrl": catalogue[source]["landing"],
            "archaeologicalContext": context(row), "rawRecord": row,
            "provenance": {"catalogueId": source, "acquisition": prov,
                           "normalizer": "build_multiscript_manifest.py", "trainingLabelStatus": "UNVERIFIED"},
            "images": []
        }
    seen = {}
    stats = {"linked_images": 0, "unlinked_images": 0, "duplicate_images": 0,
             "rejected_images": 0, "provisional_crop_regions": 0}
    for row in sorted(images, key=lambda r: (r.get("source_id", ""), r.get("image_relpath", ""))):
        source = row["source_id"]
        asset_key = hashlib.sha256(f'{source}:{row["image_relpath"]}'.encode()).hexdigest()
        try:
            path = within_root(row["image_path"], root, max_image_mb * 1024 * 1024)
        except (OSError, ValueError):
            stats["rejected_images"] += 1
            continue
        checksum = sha256(path)
        score = scores.get(str(path), scores.get(row["image_path"], {}))
        duplicates = seen.get(checksum)
        if duplicates:
            stats["duplicate_images"] += 1
        else:
            seen[checksum] = f"{source}:{row['image_relpath']}"
        candidates = by_path[row["image_path"]]
        stats["provisional_crop_regions"] += len(candidates)
        analysis = {
            "triage": score.get("triage", row.get("triage", "")) or "NOT_EVALUATED",
            "triageMargin": score.get("margin", row.get("triage_margin", "")),
            "triageExplanation": score.get("explanation", ""),
            "triageModel": "UNVERIFIED_RESEARCH_CANDIDATE",
            "linkStatus": row.get("link_status", "UNLINKED"),
            "linkBasis": row.get("link_basis", ""),
            "proposedInscriptionId": row.get("inscription_id", ""),
            "imageRelativePath": row["image_relpath"],
            "duplicateOf": duplicates,
            "provisionalCrops": candidates,
            "reviewStatus": "PENDING",
            "verifiedSignLabel": None,
        }
        asset = {"assetKey": asset_key, "sourcePath": str(path),
                 "imageKind": row.get("image_kind") or "UNCLASSIFIED",
                 "rights": row.get("image_rights") or "REVIEW_SOURCE_LICENCE",
                 "checksumSha256": checksum, "analysis": analysis}
        linked = row.get("link_status") == "LINKED" and bool(row.get("inscription_id"))
        key = (source, "inscription:" + row["inscription_id"]) if linked else None
        if linked and key not in records:
            analysis["linkStatus"] = "MISSING_RECORD"
            analysis["linkBasis"] = "referenced_inscription_not_in_manifest"
        if key not in records:
            key = (source, "image:" + asset_key)
            if key not in records:
                stable_row = {k: v for k, v in row.items() if k not in ("triage", "triage_margin")}
                acquisition = root / "scripts" / source / "acquisition.json"
                prov = json.loads(acquisition.read_text(encoding="utf-8")) if acquisition.exists() else {}
                tentative_context = context(row)
                tentative_context["inscription_id"] = ""  # Never turn weak image hints into confirmed links.
                records[key] = {
                    "sourceId": source, "recordKey": key[1], "recordType": "IMAGE_ONLY",
                    "sourceUrl": catalogue[source]["landing"],
                    "archaeologicalContext": tentative_context, "rawRecord": stable_row,
                    "provenance": {"catalogueId": source, "acquisition": prov,
                                   "normalizer": "build_multiscript_manifest.py", "trainingLabelStatus": "UNVERIFIED"},
                    "images": []
                }
            stats["unlinked_images"] += 1
        else:
            stats["linked_images"] += 1
        records[key]["images"].append(asset)
    return list(records.values()), stats


def post_record(url: str, payload: dict, token: str) -> None:
    request = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"),
                                     headers={"Content-Type": "application/json", "X-PictoGlyph-Import-Token": token}, method="POST")
    with urllib.request.urlopen(request, timeout=45) as response:
        if response.status not in (200, 201):
            raise RuntimeError(f"Unexpected API status: {response.status}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--triage-csv", type=Path, default=None)
    parser.add_argument("--run-triage", choices=("none", "inventory", "model"), default="none")
    parser.add_argument("--triage-limit", type=int, default=100)
    parser.add_argument("--max-image-mb", type=int, default=100)
    parser.add_argument("--execute", action="store_true", help="POST records to local Spring API")
    parser.add_argument("--api-url", default="http://127.0.0.1:8080/ingestion/research-records")
    args = parser.parse_args()
    if args.max_image_mb < 1 or args.triage_limit < 0:
        parser.error("Image limits must be non-negative, with --max-image-mb >= 1")
    if args.execute and urlsplit(args.api_url).hostname not in ("localhost", "127.0.0.1", "::1"):
        parser.error("Only local API endpoints supported in this pilot")
    token = os.getenv("PICTOGLYPH_RESEARCH_IMPORT_TOKEN", "")
    if args.execute and not token:
        parser.error("Set PICTOGLYPH_RESEARCH_IMPORT_TOKEN before --execute")
    manifest = args.manifest.resolve()
    root = args.root.resolve()
    triage_csv = args.triage_csv
    if args.run_triage != "none":
        if triage_csv:
            parser.error("Use either --run-triage or --triage-csv")
        sources = list(dict.fromkeys(row["source_id"] for row in read_csv(manifest / "images.csv")))
        if sources:
            triage_csv = manifest / "image_triage.csv"
            command = [sys.executable, str(TRIAGE_SCRIPT), "--root", str(root), "--output", str(triage_csv),
                       "--contact-sheet", str(manifest / "image_triage_review.jpg"), "--limit", str(args.triage_limit)]
            for source in sources:
                command += ["--source", source]
            if args.run_triage == "inventory":
                command.append("--inventory-only")
            subprocess.run(command, check=True)
    payloads, stats = build_payloads(manifest, root, args.max_image_mb, triage_csv)
    preview_path = manifest / "research_records.jsonl"
    with preview_path.open("w", encoding="utf-8") as out:
        for record in payloads:
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
    successful = 0
    failures = []
    if args.execute:
        for record in payloads:
            try:
                post_record(args.api_url, record, token)
                successful += 1
            except (urllib.error.URLError, OSError, RuntimeError) as exc:
                failures.append({"sourceId": record["sourceId"], "recordKey": record["recordKey"],
                                 "error": f"{type(exc).__name__}: {exc}"})
    summary = {"record_count": len(payloads), "images": sum(len(r["images"]) for r in payloads),
               **stats, "posted": successful, "failures": failures,
               "training_data_approved": False, "preview": str(preview_path)}
    (manifest / "research_import_summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
