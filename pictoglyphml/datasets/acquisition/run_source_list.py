#!/usr/bin/env python3
"""PG-ING-09: acquire catalogued research sources from a list of landing URLs.

Only approved catalogue entries with an existing manifest adapter are executed.
No arbitrary-site crawling, sign classification or database import occurs here.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

HERE = Path(__file__).resolve().parent
CATALOGUE = HERE / "sources.json"
DOWNLOAD_SCRIPT = HERE / "download_sources.py"
MANIFEST_SCRIPT = HERE / "build_multiscript_manifest.py"
SUPPORTED_MANIFESTS = {"indus-lipi", "cretan-hieroglyphic", "lineara-corpus"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def canonical_url(value: str) -> str:
    parsed = urlsplit(value.strip())
    if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError(f"Expected an HTTPS source landing URL: {value!r}")
    if parsed.query or parsed.fragment:
        raise ValueError(f"Source landing URL cannot contain query or fragment: {value!r}")
    path = parsed.path.rstrip("/")
    return urlunsplit(("https", parsed.netloc.lower(), path, "", ""))


def resolve_sources(urls: list[str], catalogue: list[dict]) -> list[dict]:
    by_url = {}
    for item in catalogue:
        if not item.get("landing"):
            continue
        try:
            by_url[canonical_url(item["landing"])] = item
        except ValueError:
            # Entries requiring non-standard access remain in the catalogue,
            # but are not automatically executable by this pilot.
            continue
    results = []
    seen = set()
    for url in urls:
        canonical = canonical_url(url)
        if canonical in seen:
            continue
        seen.add(canonical)
        entry = by_url.get(canonical)
        if entry is None:
            results.append({"url": url, "source_id": None, "status": "UNRECOGNISED", "message": "No exact catalogue match"})
        elif (entry.get("id") not in SUPPORTED_MANIFESTS
              or entry.get("download_support") != "supported_by_existing_downloader"
              or entry.get("state") != "ready"):
            results.append({"url": url, "source_id": entry["id"], "status": "NEEDS_ADAPTER", "message": "No complete research manifest adapter"})
        else:
            results.append({"url": url, "source_id": entry["id"], "status": "PLANNED", "message": "Catalogue and manifest adapter found"})
    return results


def run_command(args: list[str]) -> None:
    subprocess.run(args, check=True, timeout=3600)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", type=Path, required=True, help="JSON file containing the source landing URLs")
    parser.add_argument("--root", type=Path, required=True, help="Research datasets root (e.g. /mnt/i/PictoGlyph/data/research-datasets)")
    parser.add_argument("--output", type=Path, required=True, help="Run report and manifest output directory")
    parser.add_argument("--execute", action="store_true", help="Actually acquire sources and build the manifest")
    parser.add_argument("--acknowledge-access", action="store_true", help="Confirm access/usage terms were checked for these sources")
    parser.add_argument("--skip-download", action="store_true", help="Build from previously extracted source directories")
    args = parser.parse_args()

    config = json.loads(args.list.read_text(encoding="utf-8"))
    urls = config.get("sources")
    if not isinstance(urls, list) or not urls or not all(isinstance(u, str) and u.strip() for u in urls):
        parser.error("sources must be a non-empty list of landing URL strings")
    if args.execute and not args.acknowledge_access:
        parser.error("--execute requires --acknowledge-access after source terms are checked")
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))["sources"]
    results = resolve_sources(urls, catalogue)
    report = {"dataset_name": config.get("datasetName", "Research pilot"),
              "generated_at": utc_now(), "executed": args.execute, "sources": results}
    if args.execute:
        args.output.mkdir(parents=True, exist_ok=True)
        completed = []
        for result in results:
            if result["status"] != "PLANNED":
                continue
            source_id = result["source_id"]
            try:
                if not args.skip_download:
                    run_command([sys.executable, str(DOWNLOAD_SCRIPT), "download", "--ids", source_id,
                                 "--root", str(args.root), "--max-mb", "1024", "--max-extract-mb", "8192"])
                if not (args.root / "scripts" / source_id / "extracted").is_dir():
                    raise FileNotFoundError("Expected extracted source dataset is missing")
                result["status"] = "ACQUIRED"
                completed.append(source_id)
            except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
                result.update(status="FAILED", message=f"{type(exc).__name__}: {exc}")
        if completed:
            try:
                run_command([sys.executable, str(MANIFEST_SCRIPT), "--root", str(args.root),
                             "--output", str(args.output), "--sources", *completed])
            except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
                report["manifest_error"] = f"{type(exc).__name__}: {exc}"
        report["manifest_sources"] = completed
        (args.output / "source_run.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 1 if any(s["status"] in ("FAILED", "UNRECOGNISED", "NEEDS_ADAPTER") for s in results) or report.get("manifest_error") else 0


if __name__ == "__main__":
    sys.exit(main())
