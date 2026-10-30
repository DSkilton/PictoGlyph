#!/usr/bin/env python3
"""PictoGlyph research source fetcher and safe archive extractor.

Downloads explicitly requested sources, safely unpacks ZIP and JSON.GZ archives,
produces image inventories, and retains acquisition metadata. Nothing is
executed, imported to PostgreSQL, queued for ML, or classified as a glyph.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import stat
import tempfile
from collections import Counter
from pathlib import PurePosixPath
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

MANIFEST = Path(__file__).with_name("sources.json")
DEFAULT_ROOT = Path("/mnt/i/PictoGlyph/data/research-datasets")
USER_AGENT = "PictoGlyph-MSc-research-downloader/1.0 (one-at-a-time; source citations in manifest)"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def request(url: str, headers: dict[str, str] | None = None) -> urllib.request.Request:
    return urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*", **(headers or {})})


def get_json(url: str) -> dict[str, Any]:
    with urllib.request.urlopen(request(url, {"Accept": "application/json"}), timeout=60) as response:
        return json.load(response)


def github_archive(entry: dict[str, Any]) -> tuple[str, str, dict[str, Any]]:
    repo = entry["repo"]
    data = get_json(f"https://api.github.com/repos/{repo}")
    branch = data["default_branch"]
    latest = get_json(f"https://api.github.com/repos/{repo}/commits/{branch}")
    sha = latest["sha"]
    return (f"https://codeload.github.com/{repo}/zip/{sha}", f"{entry['id']}-{sha[:12]}.zip", {"git_repo": repo, "git_branch": branch, "git_commit": sha})


def zenodo_file(entry: dict[str, Any]) -> tuple[str, str, dict[str, Any]]:
    record = get_json(f"https://zenodo.org/api/records/{entry['record_id']}")
    files = record.get("files", [])
    candidates = [f for f in files if f.get("key", "").lower().endswith(".json")]
    if not candidates:
        raise RuntimeError("No JSON file found in the Zenodo record. Review the record manually.")
    chosen = candidates[0]
    url = chosen.get("links", {}).get("self")
    if not url:
        raise RuntimeError("Zenodo record did not provide a file download URL")
    return url, Path(chosen["key"]).name, {"zenodo_record": entry["record_id"], "zenodo_file": chosen["key"], "zenodo_checksum": chosen.get("checksum"), "zenodo_version": record.get("metadata", {}).get("version")}


def fetch_http(url: str, path: Path, max_bytes: int) -> None:
    """Download with best-effort HTTP Range resume and a hard file-size cap."""
    partial = path.with_name(path.name + ".part")
    for attempt in range(1, 4):
        try:
            existing = partial.stat().st_size if partial.exists() else 0
            extra = {"Range": f"bytes={existing}-"} if existing else {}
            with urllib.request.urlopen(request(url, extra), timeout=120) as response:
                code = response.status
                if code not in (200, 206):
                    raise RuntimeError(f"Unexpected HTTP status {code}")
                if code == 206:
                    content_range = response.headers.get("Content-Range", "")
                    if not content_range.startswith(f"bytes {existing}-"):
                        raise RuntimeError("Server returned an unexpected resume offset")
                    mode = "ab"
                else:
                    # Server ignored Range: restart safely, rather than append duplicates.
                    mode = "wb"
                    existing = 0
                declared = response.headers.get("Content-Length")
                if declared and existing + int(declared) > max_bytes:
                    raise RuntimeError("Download exceeds --max-mb limit based on Content-Length")
                downloaded = existing
                with partial.open(mode) as out:
                    while chunk := response.read(1024 * 1024):
                        downloaded += len(chunk)
                        if downloaded > max_bytes:
                            raise RuntimeError("Download exceeded --max-mb safety limit")
                        out.write(chunk)
            partial.replace(path)
            return
        except (OSError, urllib.error.URLError, RuntimeError) as e:
            if attempt >= 3 or "--max-mb" in str(e) or "unexpected resume offset" in str(e):
                raise
            print(f"  retry {attempt}/3 after {type(e).__name__}: {e}", flush=True)
            time.sleep(2 ** attempt)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_archive(path: Path) -> None:
    lower = path.name.lower()
    if lower.endswith((".zip", ".xlsx")) and not zipfile.is_zipfile(path):
        raise RuntimeError(f"Expected ZIP/XLSX but received another file type: {path}")
    if lower.endswith(".gz"):
        with path.open("rb") as f:
            if f.read(2) != b"\x1f\x8b":
                raise RuntimeError("Expected gzip but received another file type")
    if lower.endswith(".json"):
        with path.open("rb") as f:
            start = f.read(1024).lstrip()
        if not start.startswith((b"{", b"[")):
            raise RuntimeError("Expected JSON but received another file type")
    if lower.endswith(".ndjson"):
        with path.open("rb") as f:
            start = f.read(4096).lstrip()
        if not start.startswith(b"{"):
            raise RuntimeError("Expected newline-delimited JSON but received another file type")


# Treat SVG as a vector image candidate, not a ready-to-train raster crop.
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".tif", ".tiff", ".bmp", ".svg"}
RASTER_SUFFIXES = IMAGE_SUFFIXES - {".svg"}
METADATA_SUFFIXES = {".json", ".jsonl", ".ndjson", ".csv", ".tsv", ".xml", ".xlsx", ".parquet", ".txt"}
MAX_EXTRACT_FILES = 100_000


def is_extractable(path: Path) -> bool:
    # An XLSX/DOCX is itself a ZIP container, but should remain a spreadsheet/document.
    return path.name.lower().endswith((".zip", ".json.gz", ".ndjson.gz"))


def validate_zip_member(info: zipfile.ZipInfo) -> PurePosixPath:
    name = info.filename
    if "\\" in name or "\x00" in name or name.startswith("/"):
        raise RuntimeError(f"Unsafe ZIP path: {name!r}")
    parts = PurePosixPath(name).parts
    if not parts or any(part in (".", "..") for part in parts) or ":" in parts[0]:
        raise RuntimeError(f"Unsafe ZIP path: {name!r}")
    mode = (info.external_attr >> 16) & 0xffff
    if stat.S_IFMT(mode) == stat.S_IFLNK:
        raise RuntimeError(f"ZIP contains symlink: {name!r}")
    if info.flag_bits & 0x1:
        raise RuntimeError(f"Encrypted ZIP entry cannot be inspected: {name!r}")
    return PurePosixPath(*parts)


def extract_zip_safely(archive: Path, staging: Path, max_bytes: int) -> None:
    with zipfile.ZipFile(archive) as zf:
        infos = zf.infolist()
        if len(infos) > MAX_EXTRACT_FILES:
            raise RuntimeError(f"ZIP contains {len(infos)} entries; exceeds {MAX_EXTRACT_FILES:,} limit")
        total = sum(item.file_size for item in infos if not item.is_dir())
        if total > max_bytes:
            raise RuntimeError(f"Unpacked size {total:,} exceeds --max-extract-mb limit")
        available = shutil.disk_usage(staging.parent).free
        if total + 512 * 1024 * 1024 > available:
            raise RuntimeError("Not enough free disk space to extract archive safely")
        seen = set()
        for item in infos:
            relative = validate_zip_member(item)
            key = str(relative)
            if key in seen:
                raise RuntimeError(f"Duplicate member path in ZIP: {key}")
            seen.add(key)
            target = staging.joinpath(*relative.parts)
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            if item.file_size > max_bytes:
                raise RuntimeError(f"Oversized ZIP member: {key}")
            target.parent.mkdir(parents=True, exist_ok=True)
            # Extract as ordinary data only: never preserve executable permissions or links.
            with zf.open(item) as src, target.open("xb") as dst:
                remaining = item.file_size
                while remaining:
                    chunk = src.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise RuntimeError(f"Truncated ZIP member: {key}")
                    dst.write(chunk)
                    remaining -= len(chunk)
                if src.read(1):
                    raise RuntimeError(f"Member size mismatch: {key}")


def extract_gzip_safely(archive: Path, staging: Path, max_bytes: int) -> None:
    output_name = archive.name[:-3]
    if not output_name or "/" in output_name or "\\" in output_name:
        raise RuntimeError("Invalid compressed filename")
    size = 0
    with gzip.open(archive, "rb") as src, (staging / output_name).open("xb") as dst:
        for chunk in iter(lambda: src.read(1024 * 1024), b""):
            size += len(chunk)
            if size > max_bytes:
                raise RuntimeError("Unpacked gzip exceeds --max-extract-mb limit")
            dst.write(chunk)


def inventory_extract(folder: Path, extracted: Path, entry: dict[str, Any], archive: Path, checksum: str) -> dict[str, Any]:
    files = sorted(p for p in extracted.rglob("*") if p.is_file())
    counts = Counter(p.suffix.lower() or "[none]" for p in files)
    images = [p for p in files if p.suffix.lower() in IMAGE_SUFFIXES]
    table = folder / "image_inventory.csv"
    with table.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["source_id", "relative_path", "format", "file_size_bytes", "image_kind", "glyph_label_status"])
        for p in images:
            writer.writerow([entry["id"], p.relative_to(extracted).as_posix(), p.suffix.lower(), p.stat().st_size,
                             "raster" if p.suffix.lower() in RASTER_SUFFIXES else "vector", "unreviewed"])
    result = {
        "source_id": entry["id"],
        "archive_filename": archive.name,
        "archive_sha256": checksum,
        "extracted_at_utc": utc_now(),
        "extracted_directory": str(extracted),
        "file_count": len(files),
        "file_extensions": dict(sorted(counts.items())),
        "raster_image_count": sum(counts[s] for s in RASTER_SUFFIXES),
        "vector_image_count": counts[".svg"],
        "metadata_candidate_count": sum(counts[s] for s in METADATA_SUFFIXES),
        "image_inventory": str(table),
        "image_examples": [p.relative_to(extracted).as_posix() for p in images[:10]],
        "warning": "Image files have NOT been validated as glyph crops or matched to labels. Website thumbnails, artwork, duplicate files and unrelated graphics may be included. Review rights and provenance before ML training.",
    }
    (folder / "inventory.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return result


def extract_one(entry: dict[str, Any], root: Path, max_extract_mb: int, force_extract: bool = False) -> None:
    folder = root / entry["group"] / entry["id"]
    if not folder.exists():
        print(f"No downloaded data for {entry['id']}")
        return
    archives = sorted(path for path in folder.iterdir() if path.is_file() and is_extractable(path))
    if not archives:
        print(f"No ZIP or JSON.GZ archive to extract for {entry['id']} (raw files left as-is)")
        return
    if len(archives) > 1:
        print(f"  {len(archives)} archives detected; selecting most recently modified")
        archives.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    archive = archives[0]
    checksum = sha256_file(archive)
    dest = folder / "extracted"
    inventory_path = folder / "inventory.json"
    if inventory_path.exists() and dest.is_dir() and not force_extract:
        try:
            previous = json.loads(inventory_path.read_text(encoding="utf-8"))
            if previous.get("archive_sha256") == checksum:
                print(f"SKIP already extracted {entry['id']}: {dest} ({previous.get('raster_image_count', 0):,} raster images)")
                return
        except (json.JSONDecodeError, OSError):
            pass
    if dest.exists() and not (folder / "inventory.json").exists():
        raise RuntimeError(f"Refusing to overwrite untracked extracted directory: {dest}")
    stage = Path(tempfile.mkdtemp(prefix=".extracting-", dir=folder))
    backup = folder / ".extracted-previous"
    if backup.exists():
        raise RuntimeError(f"Previous extraction backup exists; inspect before continuing: {backup}")
    print(f"Extracting {entry['id']} from {archive.name}...", flush=True)
    try:
        cap = max_extract_mb * 1024 * 1024
        if archive.name.lower().endswith(".zip"):
            extract_zip_safely(archive, stage, cap)
        else:
            extract_gzip_safely(archive, stage, cap)
        had_old = dest.exists()
        if had_old:
            dest.rename(backup)
        try:
            stage.rename(dest)
        except BaseException:
            if had_old and backup.exists():
                backup.rename(dest)
            raise
        try:
            summary = inventory_extract(folder, dest, entry, archive, checksum)
        except BaseException:
            if had_old and backup.exists():
                shutil.rmtree(dest)
                backup.rename(dest)
            raise
        if had_old and backup.exists():
            shutil.rmtree(backup)
        print(f"  Extracted {summary['file_count']:,} files: "
              f"{summary['raster_image_count']:,} raster images, "
              f"{summary['vector_image_count']:,} SVG, "
              f"{summary['metadata_candidate_count']:,} metadata candidates", flush=True)
        print(f"  Inventory: {folder / 'inventory.json'}", flush=True)
        if not (summary["raster_image_count"] or summary["vector_image_count"]):
            print("  NOTE: no image assets found; this appears to be a text/metadata source, not an image dataset.", flush=True)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def download_one(entry: dict[str, Any], root: Path, max_mb: int, force: bool, do_extract: bool, max_extract_mb: int, force_extract: bool) -> None:
    if entry["state"] != "ready":
        raise RuntimeError("Source is not marked ready; inspect its landing page and rights first")
    folder = root / entry["group"] / entry["id"]
    folder.mkdir(parents=True, exist_ok=True)
    extras: dict[str, Any] = {}
    method = entry["method"]
    if method == "github":
        url, name, extras = github_archive(entry)
    elif method == "zenodo":
        url, name, extras = zenodo_file(entry)
    elif method == "http":
        url, name = entry["url"], entry["filename"]
    elif method == "gdrive":
        url, name = f"https://drive.google.com/file/d/{entry['file_id']}/view", entry["filename"]
        extras["google_drive_id"] = entry["file_id"]
    else:
        raise ValueError(f"Unsupported download method {method}")
    destination = folder / name
    metadata_path = folder / "acquisition.json"
    if destination.exists() and not force:
        print(f"SKIP existing download: {destination} (use --force to re-fetch)", flush=True)
        if do_extract:
            extract_one(entry, root, max_extract_mb, force_extract)
        return
    if force:
        destination.unlink(missing_ok=True)
        destination.with_name(destination.name + ".part").unlink(missing_ok=True)
    print(f"Downloading {entry['id']} -> {destination}", flush=True)
    if method == "gdrive":
        if shutil.which("gdown") is None:
            raise RuntimeError("Google Drive sources require: python -m pip install gdown")
        partial = destination.with_name(destination.name + ".part")
        subprocess.run(["gdown", "--continue", entry["file_id"], "-O", str(partial)], check=True)
        if partial.stat().st_size > max_mb * 1024 * 1024:
            raise RuntimeError("Google Drive result exceeds --max-mb limit")
        partial.replace(destination)
    else:
        fetch_http(url, destination, max_mb * 1024 * 1024)
    try:
        validate_archive(destination)
    except Exception:
        # Keep unexpected files for examination but do not label them as successful.
        bad = destination.with_name(destination.name + ".unexpected")
        destination.replace(bad)
        raise
    provenance = {
        "source_id": entry["id"], "name": entry["label"], "script_ids": entry["scripts"],
        "data_kind": entry["kind"], "source_landing_page": entry["landing"],
        "source_url": url, "licence_declared": entry["licence"],
        "caution": entry.get("caution"), "retrieved_at_utc": utc_now(),
        "local_filename": destination.name, "file_size_bytes": destination.stat().st_size,
        "sha256": sha256_file(destination), **extras,
    }
    metadata_path.write_text(json.dumps(provenance, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  OK {destination.stat().st_size:,} bytes sha256={provenance['sha256'][:16]}…", flush=True)
    if do_extract:
        extract_one(entry, root, max_extract_mb, force_extract)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["list", "download", "extract"])
    parser.add_argument("--ids", nargs="*", default=[], help="Specific source IDs to select")
    parser.add_argument("--group", choices=["scripts", "translation_keys", "context", "trade"])
    parser.add_argument("--all-ready", action="store_true", help="Explicitly download ALL ready sources in scope")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--max-mb", type=int, default=1024, help="Maximum size of one downloaded file (default: 1024 MiB)")
    parser.add_argument("--force", action="store_true", help="Replace existing downloads")
    parser.add_argument("--no-extract", action="store_true", help="Download without extracting ZIP/JSON.GZ archives")
    parser.add_argument("--force-extract", action="store_true", help="Rebuild previously extracted files, even if checksum unchanged")
    parser.add_argument("--max-extract-mb", type=int, default=8192, help="Maximum total uncompressed size of an archive (default 8192 MiB)")
    args = parser.parse_args()
    catalogue = json.loads(MANIFEST.read_text(encoding="utf-8"))["sources"]
    if len({x["id"] for x in catalogue}) != len(catalogue):
        parser.error("Duplicate IDs in sources.json")
    known = {e["id"] for e in catalogue}
    unknown = set(args.ids) - known
    if unknown:
        parser.error("Unknown IDs: " + ", ".join(sorted(unknown)))
    entries = [e for e in catalogue if (not args.group or e["group"] == args.group) and (not args.ids or e["id"] in args.ids)]
    if args.command == "list":
        for e in entries:
            print(f"{e['id']:<26} {e['state']:<7} {e['group']:<17} {e['kind']:<29} {e['label']}")
        print(f"\n{len(entries)} sources shown. Only 'ready' sources can be downloaded.")
        return 0
    if not args.ids and not args.all_ready:
        parser.error("Download/extract requires --ids or --all-ready; nothing starts by default")
    entries = [e for e in entries if e["state"] == "ready"]
    if not entries:
        print("No ready sources selected")
        return 1
    if args.dry_run:
        for e in entries:
            print(f"Would {args.command} {e['id']} [{e['method']}] at {args.root / e['group'] / e['id']}")
        return 0
    if args.max_mb < 1 or args.max_extract_mb < 1:
        parser.error("--max-mb and --max-extract-mb must be positive")
    failures = []
    for index, entry in enumerate(entries):
        try:
            if args.command == "extract":
                extract_one(entry, args.root, args.max_extract_mb, args.force_extract)
            else:
                download_one(entry, args.root, args.max_mb, args.force, not args.no_extract, args.max_extract_mb, args.force_extract)
        except (Exception, KeyboardInterrupt) as exc:
            if isinstance(exc, KeyboardInterrupt):
                raise
            print(f"ERROR {entry['id']}: {exc}", file=sys.stderr, flush=True)
            failures.append(entry["id"])
        if index + 1 < len(entries):
            time.sleep(2)  # one at a time, no aggressive parallel scraping
    print(f"Done: {len(entries) - len(failures)} successful/skipped, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
