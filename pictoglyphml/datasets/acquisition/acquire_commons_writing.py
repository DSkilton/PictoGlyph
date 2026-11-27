"""Preview and acquire licensed writing photographs from Commons, locally only.

Search terms are discovery hints, never verified script, material or character labels.
The existing Met adapter retains its independent P3634 and CC0-only policy.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

import acquire_met_commons_bulk as commons
from acquisition_constants import COMMONS_API_HOST, COMMONS_UPLOAD_HOST, DOWNLOADED, JPEG_CONTENT_TYPE
from acquisition_io import append_jsonl, sha256_file, utc_now, write_json_atomic

CATALOGUE = Path(__file__).with_name("modern_script_sources.json")
LICENSES = {"/publicdomain/zero/1.0/": "CC0-1.0", "/licenses/by/4.0/": "CC-BY-4.0", "/licenses/by-sa/4.0/": "CC-BY-SA-4.0", "/licenses/by/3.0/": "CC-BY-3.0", "/licenses/by-sa/3.0/": "CC-BY-SA-3.0"}
SCHEMA_VERSION = 1


def trusted_url(url, host):
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != host or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError(f"Untrusted URL for {host}")
    return url


def license_id(url):
    parsed = urlsplit(url.strip())
    if parsed.scheme not in ("http", "https") or parsed.hostname != "creativecommons.org" or parsed.username or parsed.password or parsed.port is not None or parsed.query or parsed.fragment:
        raise ValueError("Unverified licence URL")
    path = parsed.path.rstrip("/") + "/"
    if path not in LICENSES:
        raise ValueError("Licence outside pilot allowlist")
    return LICENSES[path]


def field(metadata, name):
    value = metadata.get(name, {})
    return str(value.get("value", "")) if isinstance(value, dict) else ""


def parse_imageinfo(response, pageid, max_bytes):
    if "error" in response:
        raise ValueError(f"Commons API error: {response['error']}")
    pages = response.get("query", {}).get("pages", [])
    if len(pages) != 1 or pages[0].get("pageid") != pageid or pages[0].get("ns") != 6 or not pages[0].get("imageinfo"):
        raise ValueError("Missing or mismatched Commons file identity")
    page = pages[0]
    image = page["imageinfo"][0]
    if image.get("mime") != JPEG_CONTENT_TYPE:
        raise ValueError("Pilot only supports original JPEG files")
    size = image.get("size")
    if type(size) is not int or not 0 < size <= max_bytes:
        raise ValueError("Missing or oversized original image")
    sha1 = str(image.get("sha1", "")).lower()
    if not (re.fullmatch(r"[0-9a-f]{40}", sha1) or re.fullmatch(r"[0-9a-z]{1,31}", sha1)) or int(sha1, 16 if len(sha1) == 40 else 36) >= 2**160:
        raise ValueError("Missing or invalid upstream SHA-1")
    metadata = image.get("extmetadata", {})
    licence_url = field(metadata, "LicenseUrl")
    licence = license_id(licence_url)
    if field(metadata, "Restrictions").strip():
        raise ValueError("Additional rights restrictions require review")
    if licence != "CC0-1.0" and not field(metadata, "Artist").strip():
        raise ValueError("Attribution missing; manual review required")
    return {"pageid": pageid, "title": page["title"], "downloadUrl": trusted_url(image["url"], COMMONS_UPLOAD_HOST), "pageUrl": trusted_url(image["descriptionurl"], COMMONS_API_HOST), "license": licence, "licenseUrl": licence_url, "commonsSha1": sha1, "bytesReported": size, "sourceMetadata": metadata, "trainingDataApproved": False}


def imageinfo(pageid, agent, timeout, max_bytes):
    response = commons.commons_query({"prop": "imageinfo", "pageids": str(pageid), "iiprop": "url|mime|size|extmetadata|sha1"}, agent, timeout)
    return parse_imageinfo(response, pageid, max_bytes)


def search(query, limit, agent, timeout):
    ids = []
    continuation = {}
    seen_offsets = set()
    for _ in range(limit):
        if len(ids) >= limit:
            break
        params = {"list": "search", "srsearch": query, "srnamespace": "6", "srlimit": str(min(50, limit - len(ids))), **continuation}
        response = commons.commons_query(params, agent, timeout)
        if "error" in response:
            raise ValueError(f"Commons search error: {response['error']}")
        rows = response.get("query", {}).get("search")
        if not isinstance(rows, list):
            raise ValueError("Invalid search response")
        for row in rows:
            pageid = row.get("pageid")
            if row.get("ns") == 6 and type(pageid) is int and pageid > 0 and pageid not in ids:
                ids.append(pageid)
        next_page = response.get("continue")
        if not next_page or next_page == continuation or not rows:
            break
        continuation = {k: v for k, v in next_page.items() if k in ("continue", "sroffset")}
        offset = continuation.get("sroffset")
        if type(offset) is not int or offset in seen_offsets:
            break
        seen_offsets.add(offset)
    return ids[:limit]


def load_query(script, medium):
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    if script not in catalogue["scriptQueries"] or medium not in catalogue["mediumQueries"]:
        raise ValueError("Unknown script or medium; use list to inspect supported values")
    return f'{catalogue["scriptQueries"][script]} {catalogue["mediumQueries"][medium]}'


def verify_upstream(path, info):
    if path.stat().st_size != info["bytesReported"]:
        raise ValueError("Downloaded size differs from upstream metadata")
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    expected = info["commonsSha1"]
    if int(digest.hexdigest(), 16) != int(expected, 16 if len(expected) == 40 else 36):
        raise ValueError("Downloaded SHA-1 differs from upstream metadata")
    return sha256_file(path)


def evidence_matches(previous, current):
    return all(previous.get(key) == current.get(key) for key in ("pageid", "title", "downloadUrl", "pageUrl", "license", "licenseUrl", "commonsSha1", "bytesReported", "sourceMetadata"))


def preview(args, root):
    plan = root / "commons-writing-plan.json"
    if plan.exists() or plan.is_symlink():
        raise ValueError("Plan already exists; select another output directory for a new preview")
    query = load_query(args.script, args.medium)
    candidates = []
    for pageid in search(query, args.limit, args.user_agent, args.timeout):
        try:
            info = imageinfo(pageid, args.user_agent, args.timeout, args.max_image_mb * 1024**2)
            candidates.append({"status": "PREVIEW_READY", **info})
        except (ValueError, KeyError) as error:
            candidates.append({"pageid": pageid, "status": "REVIEW_REQUIRED", "reason": str(error)})
        print(f"Commons {pageid}: {candidates[-1]['status']}", flush=True)
    write_json_atomic(plan, {"schemaVersion": SCHEMA_VERSION, "createdAt": utc_now(), "scriptQuery": args.script, "mediumQuery": args.medium, "query": query, "trainingDataApproved": False, "candidates": candidates})
    print(f"Plan: {plan}; eligible={sum(x['status'] == 'PREVIEW_READY' for x in candidates)}/{len(candidates)}")
    return 0


def download(args, root):
    plan = root / "commons-writing-plan.json"
    if plan.is_symlink():
        raise ValueError("Plan must not be a symlink")
    data = json.loads(plan.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != SCHEMA_VERSION or data.get("query") != load_query(data["scriptQuery"], data["mediumQuery"]):
        raise ValueError("Invalid or obsolete acquisition plan")
    candidates = data["candidates"]
    ids = [item.get("pageid") for item in candidates]
    if len(ids) > 1000 or any(type(x) is not int or x < 1 for x in ids) or len(ids) != len(set(ids)):
        raise ValueError("Invalid or duplicate candidate IDs")
    images = root / "images"
    if images.is_symlink():
        raise ValueError("Image directory must not be a symlink")
    images.mkdir(exist_ok=True)
    audit = root / "commons-writing-audit.jsonl"
    if audit.is_symlink():
        raise ValueError("Audit path must not be a symlink")
    plan_hash = sha256_file(plan)
    failures = 0
    eligible = sum(item.get("status") == "PREVIEW_READY" for item in candidates)
    if not eligible:
        print("No eligible images in this plan; review discovery results", file=sys.stderr)
        return 0 if getattr(args, "continue_on_item_error", False) else 1
    for item in candidates:
        if item.get("status") != "PREVIEW_READY":
            continue
        pageid = item["pageid"]
        row = {"pageid": pageid, "timestamp": utc_now(), "planSha256": plan_hash, "trainingDataApproved": False}
        try:
            current = imageinfo(pageid, args.user_agent, args.timeout, args.max_image_mb * 1024**2)
            if not evidence_matches(item, current):
                raise ValueError("Source identity, content or metadata changed since preview; review again")
            destination = images / f"{pageid}.jpg"
            outcome, metadata = commons.acquire_file(current, destination, args.user_agent, args.timeout, args.max_image_mb * 1024**2)
            checksum = verify_upstream(destination, current)
            if checksum != metadata["sha256"] or not evidence_matches(metadata, current):
                raise ValueError("Stored image evidence differs from current source")
            if outcome == DOWNLOADED:
                metadata.update(retrievedAt=utc_now(), planSha256=plan_hash, imageValidation="FULL_JPEG_DECODE_AND_UPSTREAM_SHA1", trainingDataApproved=False)
                write_json_atomic(destination.with_suffix(".json"), metadata)
            row.update(status=outcome, sha256=checksum, localPath=str(destination))
        except commons.AcquisitionNetworkError as error:
            row.update(status="PAUSED", reason=str(error), retryAfterSeconds=getattr(error, "retry_after_seconds", None))
            append_jsonl(audit, row)
            print(f"Paused: {error}; rerun after the server cooldown", file=sys.stderr)
            return 1
        except (OSError, ValueError, KeyError) as error:
            row.update(status="FAILED", reason=str(error))
            failures += 1
        append_jsonl(audit, row)
        print(f"Commons {pageid}: {row['status']}", flush=True)
    if failures:
        print(f"Plan completed with {failures} individually audited failed image(s)", file=sys.stderr)
    return 1 if failures and not getattr(args, "continue_on_item_error", False) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("list", "preview", "download"))
    parser.add_argument("--script")
    parser.add_argument("--medium")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--max-image-mb", type=int, default=25)
    parser.add_argument("--timeout", type=float, default=45)
    parser.add_argument("--pause-seconds", type=float, default=5)
    parser.add_argument("--user-agent", default=os.environ.get("PICTOGLYPH_COMMONS_USER_AGENT", commons.DEFAULT_USER_AGENT))
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--continue-on-item-error", action="store_true", help="Finish this plan and continue the overnight sequence if individual images fail; always audit failures")
    args = parser.parse_args()
    if args.command == "list":
        catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
        print("Scripts:", ", ".join(catalogue["scriptQueries"]))
        print("Media:", ", ".join(catalogue["mediumQueries"]))
        return 0
    if not args.output_dir or not args.output_dir.is_absolute() or not 1 <= args.limit <= 1000 or not 1 <= args.max_image_mb <= 100 or not 5 <= args.pause_seconds <= 300 or not 0 < args.timeout <= 300:
        parser.error("Use an absolute output directory, limit 1–1000, size 1–100 MiB, pause 5–300s and timeout 0–300s")
    if args.command == "download" and not args.execute:
        parser.error("Download requires --execute after reviewing the preview plan")
    if args.command == "preview" and (args.execute or not args.script or not args.medium):
        parser.error("Preview requires --script and --medium, without --execute")
    root = args.output_dir
    if root.is_symlink():
        parser.error("Output directory must not be a symlink")
    root = root.resolve()
    root.mkdir(parents=True, exist_ok=True)
    commons.MIN_WIKIMEDIA_INTERVAL = args.pause_seconds
    try:
        return preview(args, root) if args.command == "preview" else download(args, root)
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print(f"Commons acquisition stopped: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
