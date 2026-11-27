#!/usr/bin/env python3
"""Resumable Wikimedia Commons acquisition for Met research candidates.

Uses Commons structured-data P3634 as the matching key. Does not use Met image
CDN URLs, infer copyright from filenames, or approve images for ML.
Run from the PictoGlyph repository root or set PYTHONPATH to the acquisition dir.
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
import time
import warnings

from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import ProxyHandler, Request, build_opener

from PIL import Image, UnidentifiedImageError

from import_met_object_ids import NoRedirectHandler, SOURCE_ID, load_candidates, sha256_file, validate_api_base

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
MET_PROPERTY = "P3634"
DEFAULT_USER_AGENT = "PictoGlyphResearch/0.1 (academic acquisition; https://github.com/DSkilton/PictoGlyph)"
RETRYABLE_HTTP = {429, 500, 502, 503, 504}
CHUNK_SIZE = 1024 * 1024
MAX_API_BYTES = 8 * 1024 * 1024
MAX_MATCHES = 50
TERMINAL_STATUSES = {"ATTACHED", "DOWNLOADED", "NO_STRUCTURED_MATCH", "REVIEW_REQUIRED", "LEGACY_PILOT_ALREADY_ATTACHED"}
LEGACY_PILOT_OBJECT_IDS = {550876, 550878}
MAX_NETWORK_ATTEMPTS = 3
MAX_AUTOMATIC_RETRY_SECONDS = 120
TRANSIENT_COMMONS_API_CODES = {"cirrussearch-too-busy-error", "maxlag", "ratelimited"}
COMMONS_API_BUSY_INITIAL_DELAY_SECONDS = 30
COMMONS_API_BUSY_COOLDOWN_SECONDS = 180
WIKIMEDIA_HOSTS = {"commons.wikimedia.org", "upload.wikimedia.org"}
LAST_WIKIMEDIA_REQUEST = None
MIN_WIKIMEDIA_INTERVAL = 2.0


class AcquisitionNetworkError(RuntimeError):
    def __init__(self, stage, host, cause):
        self.stage = stage
        self.host = host
        self.http_status = cause.code if isinstance(cause, HTTPError) else None
        super().__init__(f"{stage} ({host}): {type(cause).__name__}"
                         + (f" HTTP {self.http_status}" if self.http_status else ""))


class RemoteCooldown(AcquisitionNetworkError):
    def __init__(self, stage, host, cause, retry_after_seconds):
        super().__init__(stage, host, cause)
        self.retry_after_seconds = retry_after_seconds


def pace_wikimedia(host):
    global LAST_WIKIMEDIA_REQUEST
    if host not in WIKIMEDIA_HOSTS:
        return
    now_monotonic = time.monotonic()
    if LAST_WIKIMEDIA_REQUEST is not None:
        remaining = MIN_WIKIMEDIA_INTERVAL - (now_monotonic - LAST_WIKIMEDIA_REQUEST)
        if remaining > 0:
            time.sleep(remaining)
    LAST_WIKIMEDIA_REQUEST = time.monotonic()


def retry_delay_seconds(error, attempt):
    minimum = 5 * (2 ** attempt)
    if not isinstance(error, HTTPError) or error.headers is None:
        return minimum
    raw = error.headers.get("Retry-After")
    if not raw:
        return minimum
    try:
        seconds = int(raw)
    except ValueError:
        try:
            target = parsedate_to_datetime(raw)
            if target.tzinfo is None:
                target = target.replace(tzinfo=timezone.utc)
            seconds = max(0, (target - datetime.now(timezone.utc)).total_seconds())
        except (TypeError, ValueError, OverflowError):
            return minimum
    return max(minimum, seconds)


def retry_remote(operation, url, stage):
    host = urlsplit(url).hostname or "unknown-host"
    for attempt in range(MAX_NETWORK_ATTEMPTS):
        try:
            pace_wikimedia(host)
            return operation()
        except HTTPError as error:
            if error.code not in RETRYABLE_HTTP:
                raise AcquisitionNetworkError(stage, host, error) from error
            failure = error
        except (URLError, ConnectionError, TimeoutError) as error:
            failure = error
        delay = retry_delay_seconds(failure, attempt)
        if delay > MAX_AUTOMATIC_RETRY_SECONDS:
            raise RemoteCooldown(stage, host, failure, delay) from failure
        if attempt == MAX_NETWORK_ATTEMPTS - 1:
            raise AcquisitionNetworkError(stage, host, failure) from failure
        print(f"Remote {stage} ({host}) unavailable; retrying after {delay:g}s (attempt {attempt + 2}/{MAX_NETWORK_ATTEMPTS})", file=sys.stderr, flush=True)
        time.sleep(delay)
    raise RuntimeError("Unreachable retry state")


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json_atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     prefix=".pictoglyph-", suffix=".part", delete=False) as stream:
        temporary = Path(stream.name)
        try:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    temporary.replace(path)


def append_jsonl(path, record):
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(record, ensure_ascii=False) + "\n")
        stream.flush()
        os.fsync(stream.fileno())


def request_bytes(url, user_agent, timeout=45, limit=MAX_API_BYTES, method="GET", payload=None, headers=None):
    opener = build_opener(ProxyHandler({}), NoRedirectHandler())
    target = urlsplit(url)
    if target.scheme != "https" and not (target.scheme == "http" and target.hostname in ("127.0.0.1", "localhost", "::1")):
        raise ValueError("Only HTTPS or the local PictoGlyph API are allowed")
    request_headers = {"User-Agent": user_agent, "Accept": "application/json", **(headers or {})}

    def read_once():
        request = Request(url, data=payload, headers=request_headers, method=method)
        with opener.open(request, timeout=timeout) as response:
            data = response.read(limit + 1)
            if len(data) > limit:
                raise ValueError("API response exceeds size limit")
            return data

    return retry_remote(read_once, url, "api-request" if target.hostname in WIKIMEDIA_HOSTS else "local-attachment")


def commons_json(url, user_agent, timeout):
    for attempt in range(MAX_NETWORK_ATTEMPTS):
        response = json.loads(request_bytes(url, user_agent, timeout))
        error = response.get("error") if isinstance(response, dict) else None
        code = error.get("code") if isinstance(error, dict) else None
        if code not in TRANSIENT_COMMONS_API_CODES:
            return response
        if attempt == MAX_NETWORK_ATTEMPTS - 1:
            cooldown = RemoteCooldown("api-request", "commons.wikimedia.org", RuntimeError(code), COMMONS_API_BUSY_COOLDOWN_SECONDS)
            cooldown.api_error_code = code
            raise cooldown
        delay = COMMONS_API_BUSY_INITIAL_DELAY_SECONDS * (2 ** attempt)
        print(f"Commons API busy ({code}); retrying after {delay}s (attempt {attempt + 2}/{MAX_NETWORK_ATTEMPTS})", file=sys.stderr, flush=True)
        time.sleep(delay)
    raise RuntimeError("Unreachable Commons API retry state")


def commons_query(params, user_agent, timeout):
    query = {"action": "query", "format": "json", "formatversion": "2", "maxlag": "5", **params}
    url = COMMONS_API + "?" + urlencode(query)
    return commons_json(url, user_agent, timeout)


def search_met_files(object_id, user_agent, timeout):
    response = commons_query({"list": "search", "srsearch": f"haswbstatement:{MET_PROPERTY}={object_id}",
                              "srnamespace": "6", "srlimit": str(MAX_MATCHES)}, user_agent, timeout)
    if "error" in response:
        raise ValueError(f"Commons search error: {response['error']}")
    matches = response.get("query", {}).get("search", [])
    if not isinstance(matches, list):
        raise ValueError("Commons search returned invalid results")
    if len(matches) == MAX_MATCHES or response.get("continue"):
        raise ValueError("Commons search may have more results; manual pagination required")
    return [{"pageid": item["pageid"], "title": item["title"]} for item in matches
            if item.get("ns") == 6 and isinstance(item.get("pageid"), int) and item.get("title", "").startswith("File:")]


def verify_structured_met_id(pageid, object_id, user_agent, timeout):
    query = {"action": "wbgetentities", "format": "json", "maxlag": "5", "ids": f"M{pageid}", "props": "claims"}
    response = commons_json(COMMONS_API + "?" + urlencode(query), user_agent, timeout)
    if "error" in response:
        raise ValueError(f"Commons entity error: {response['error']}")
    claims = response.get("entities", {}).get(f"M{pageid}", {}).get("statements", {}).get(MET_PROPERTY, [])
    if not claims:
        claims = response.get("entities", {}).get(f"M{pageid}", {}).get("claims", {}).get(MET_PROPERTY, [])
    values = {str(item.get("mainsnak", {}).get("datavalue", {}).get("value", "")) for item in claims}
    if values != {str(object_id)}:
        raise ValueError(f"Commons M{pageid} P3634 has unexpected values: {sorted(values)}")
    return True


def commons_imageinfo(title, user_agent, timeout):
    response = commons_query({"prop": "imageinfo", "titles": title, "iiprop": "url|mime|size|extmetadata|sha1"}, user_agent, timeout)
    pages = response.get("query", {}).get("pages", [])
    if len(pages) != 1 or not pages[0].get("imageinfo"):
        raise ValueError(f"Missing Commons image information for {title}")
    info = pages[0]["imageinfo"][0]
    fields = info.get("extmetadata", {})
    license_name = str(fields.get("LicenseShortName", {}).get("value", ""))
    license_url = str(fields.get("LicenseUrl", {}).get("value", ""))
    if info.get("mime") != "image/jpeg":
        raise ValueError(f"Commons file is not JPEG: {info.get('mime')}")
    if "cc0" not in license_name.lower() and "creativecommons.org/publicdomain/zero/1.0" not in license_url.lower():
        raise ValueError(f"Commons CC0 licence not verified: {license_name}")
    url = info.get("url", "")
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "upload.wikimedia.org" or parsed.username or parsed.password:
        raise ValueError("Commons original file URL is not an authorised HTTPS upload URL")
    page_url = info.get("descriptionurl", "")
    if urlsplit(page_url).hostname != "commons.wikimedia.org":
        raise ValueError("Commons description URL is invalid")
    return {"downloadUrl": url, "pageUrl": page_url, "license": license_name, "licenseUrl": license_url,
            "commonsSha1": info.get("sha1"), "bytesReported": info.get("size")}


def validate_jpeg_file(path):
    """Validate a complete JPEG without discarding legitimate trailing metadata."""
    with path.open("rb") as stream:
        if stream.read(3) != b"\xff\xd8\xff":
            raise ValueError("Commons download lacks a JPEG start marker")
        stream.seek(0)
        previous = b""
        eoi_found = False
        for block in iter(lambda: stream.read(CHUNK_SIZE), b""):
            if b"\xff\xd9" in previous + block:
                eoi_found = True
            previous = block[-1:]
    if not eoi_found:
        raise ValueError("Commons JPEG lacks an end-of-image marker")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(path) as image:
                if image.format != "JPEG":
                    raise ValueError("Commons download is not a JPEG")
                image.load()
    except (OSError, UnidentifiedImageError, Image.DecompressionBombWarning) as error:
        raise ValueError("Commons JPEG cannot be fully decoded") from error


def sha256_file_verified(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Image path is not a regular file: {path}")
    validate_jpeg_file(path)
    return {"sha256": sha256_file(path), "fileSize": path.stat().st_size}


def acquire_file_once(info, destination, user_agent, timeout, max_bytes):
    metadata_path = destination.with_suffix(".json")
    if destination.exists() or metadata_path.exists():
        if not destination.is_file() or not metadata_path.is_file() or destination.is_symlink() or metadata_path.is_symlink():
            raise ValueError("Incomplete or symlinked local image/metadata pair")
        previous = json.loads(metadata_path.read_text(encoding="utf-8"))
        verified = sha256_file_verified(destination)
        if previous.get("downloadUrl") != info["downloadUrl"] or any(previous.get(key) != value for key, value in verified.items()):
            raise ValueError("Existing image differs from recorded evidence")
        return "REUSED", previous

    destination.parent.mkdir(parents=True, exist_ok=True)
    url = info["downloadUrl"]
    opener = build_opener(ProxyHandler({}), NoRedirectHandler())
    request = Request(url, headers={"User-Agent": user_agent, "Accept": "image/jpeg"})
    temporary = None
    try:
        with opener.open(request, timeout=timeout) as response:
            content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
            if content_type != "image/jpeg":
                raise ValueError(f"Commons returned non-JPEG content type: {content_type}")
            size_header = response.headers.get("Content-Length")
            if size_header is not None and int(size_header) > max_bytes:
                raise ValueError("Commons image exceeds configured maximum")
            digest = hashlib.sha256()
            size = 0
            with tempfile.NamedTemporaryFile(mode="wb", prefix=".commons-", suffix=".part", dir=destination.parent, delete=False) as stream:
                temporary = Path(stream.name)
                for block in iter(lambda: response.read(CHUNK_SIZE), b""):
                    size += len(block)
                    if size > max_bytes:
                        raise ValueError("Commons image exceeded size limit")
                    digest.update(block)
                    stream.write(block)
                stream.flush()
                os.fsync(stream.fileno())
            validate_jpeg_file(temporary)
            if destination.exists() or metadata_path.exists():
                raise ValueError("Destination appeared during download; refusing overwrite")
            temporary.replace(destination)
            temporary = None
            return "DOWNLOADED", {"sha256": digest.hexdigest(), "fileSize": size, **info}
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def acquire_file(info, destination, user_agent, timeout, max_bytes):
    return retry_remote(lambda: acquire_file_once(info, destination, user_agent, timeout, max_bytes),
                        info["downloadUrl"], "image-download")


def local_post(api_base, object_id, pageid, metadata, image_path, user_agent, timeout, token):
    path = f"/ingestion/research-records/{SOURCE_ID}/records/object:{object_id}/image-assets"
    asset = {"assetKey": f"commons:met:{object_id}:{pageid}", "sourcePath": str(image_path.resolve()),
             "imageKind": "LOCAL_RESEARCH_IMAGE", "rights": "CC0-1.0", "checksumSha256": metadata["sha256"],
             "analysis": {"distributionSource": "Wikimedia Commons", "distributionFilePage": metadata["pageUrl"],
                          "distributionUrl": metadata["downloadUrl"], "commonsPageId": pageid,
                          "commonsLicence": metadata["license"], "commonsLicenceUrl": metadata["licenseUrl"],
                          "commonsSha1": metadata.get("commonsSha1"), "metObjectId": object_id,
                          "matchProperty": MET_PROPERTY, "matchMethod": "VERIFIED_COMMONS_STRUCTURED_ID",
                          "downloadStatus": "DOWNLOADED_LOCAL", "fileSizeBytes": metadata["fileSize"],
                          "trainingDataApproved": False}}
    url = api_base + path
    payload = json.dumps(asset).encode("utf-8")
    response = json.loads(request_bytes(url, user_agent, timeout, method="POST", payload=payload,
                        headers={"Content-Type": "application/json", "X-PictoGlyph-Import-Token": token}))
    if response.get("imageAssetsCreated") not in (0, 1) or response.get("imageAssetsUpdated") != 0:
        raise ValueError(f"Unexpected attach response for Met {object_id}: {response}")
    return response


def process_object(object_id, args, root, token):
    if args.attach and object_id in LEGACY_PILOT_OBJECT_IDS:
        return {"status": "LEGACY_PILOT_ALREADY_ATTACHED", "files": []}
    matches = search_met_files(object_id, args.user_agent, args.timeout)
    if not matches:
        return {"status": "NO_STRUCTURED_MATCH", "files": []}
    if len(matches) > args.max_images_per_object:
        return {"status": "REVIEW_REQUIRED", "reason": "TOO_MANY_MATCHES", "matches": matches, "files": []}
    verified = []
    issues = []
    for match in matches:
        try:
            verify_structured_met_id(match["pageid"], object_id, args.user_agent, args.timeout)
            info = commons_imageinfo(match["title"], args.user_agent, args.timeout)
            verified.append({**match, **info})
        except (ValueError, KeyError) as error:
            issues.append({"pageid": match["pageid"], "title": match["title"], "reason": str(error)})
        time.sleep(args.pause_seconds)
    if issues:
        return {"status": "REVIEW_REQUIRED", "reason": "MATCH_OR_LICENCE_UNVERIFIED", "issues": issues, "matches": matches, "files": []}
    if not args.execute:
        return {"status": "PREVIEW_READY", "files": verified}
    files = []
    for item in verified:
        image_path = root / "images" / "commons-met" / str(object_id) / f"{item['pageid']}.jpg"
        outcome, metadata = acquire_file(item, image_path, args.user_agent, args.timeout, args.max_image_mb * 1024 * 1024)
        if outcome == "DOWNLOADED":
            write_json_atomic(image_path.with_suffix(".json"), metadata)
        attachment = None
        if args.attach:
            attachment = local_post(args.api_base, object_id, item["pageid"], metadata, image_path, args.user_agent,
                                    args.timeout, token)
        files.append({"pageid": item["pageid"], "title": item["title"], "localPath": str(image_path),
                      "sha256": metadata["sha256"], "outcome": outcome, "attachment": attachment})
        time.sleep(args.pause_seconds)
    return {"status": "ATTACHED" if args.attach else "DOWNLOADED", "files": files}


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--max-objects", type=int, default=10)
    parser.add_argument("--max-images-per-object", type=int, default=20)
    parser.add_argument("--max-image-mb", type=int, default=25)
    parser.add_argument("--pause-seconds", type=float, default=2.0)
    parser.add_argument("--timeout", type=float, default=45)
    parser.add_argument("--user-agent", default=os.environ.get("PICTOGLYPH_COMMONS_USER_AGENT", DEFAULT_USER_AGENT))
    parser.add_argument("--api-base", default="http://127.0.0.1:8080")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--attach", action="store_true", help="Attach files to existing PostgreSQL research records (requires --execute)")
    parser.add_argument("--retry-unmatched", action="store_true", help="Revisit NO_STRUCTURED_MATCH and REVIEW_REQUIRED entries")
    return parser


def run(args):
    if args.max_objects < 1 or not 1 <= args.max_images_per_object <= MAX_MATCHES or not 1 <= args.max_image_mb <= 100:
        raise ValueError("Invalid object, image-count or size limits")
    if args.pause_seconds < 1.0 or args.timeout <= 0:
        raise ValueError("Requests must have at least 1s pause and a positive timeout")
    global MIN_WIKIMEDIA_INTERVAL
    MIN_WIKIMEDIA_INTERVAL = args.pause_seconds
    if args.attach and not args.execute:
        raise ValueError("--attach requires --execute")
    api_base = validate_api_base(args.api_base)
    args.api_base = api_base
    token = os.environ.get("PICTOGLYPH_RESEARCH_IMPORT_TOKEN", "")
    if args.attach and not token:
        raise ValueError("Attaching to PostgreSQL requires PICTOGLYPH_RESEARCH_IMPORT_TOKEN")
    source = args.input.expanduser().resolve(strict=True)
    if args.output_dir.expanduser().is_symlink():
        raise ValueError("Output root must not be a symbolic link")
    root = args.output_dir.expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    ids, excluded_rights = load_candidates(source)
    selected_ids = ids[:args.max_objects]
    source_hash = sha256_file(source)
    checkpoint_path = root / "met-commons-checkpoint.json"
    audit_path = root / "met-commons-audit.jsonl"
    report_path = root / "met-commons-last-run.json"
    if checkpoint_path.exists():
        checkpoint = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        if checkpoint.get("sourceSha256") != source_hash:
            raise ValueError("Checkpoint belongs to a different shortlist; choose another output directory")
    else:
        checkpoint = {"schemaVersion": 1, "sourceId": SOURCE_ID, "sourceSha256": source_hash, "processed": {}}
    results = []
    consecutive_network_failures = 0
    paused_for_network = False
    for index, object_id in enumerate(selected_ids, 1):
        prior = checkpoint["processed"].get(str(object_id))
        if args.execute and prior and prior["status"] in TERMINAL_STATUSES and (not args.retry_unmatched or prior["status"] not in {"NO_STRUCTURED_MATCH", "REVIEW_REQUIRED"}):
            if args.attach and prior["status"] == "DOWNLOADED":
                pass
            else:
                results.append({"objectId": object_id, "status": "ALREADY_PROCESSED", "previous": prior["status"]})
                continue
        try:
            outcome = process_object(object_id, args, root, token)
            entry = {"objectId": object_id, "timestamp": now(), **outcome}
            consecutive_network_failures = 0
            print(f"[{index}/{len(selected_ids)}] Met {object_id}: {outcome['status']} ({len(outcome.get('files', []))} files)", flush=True)
        except (OSError, ValueError, RuntimeError, HTTPError, URLError, TimeoutError, KeyError, json.JSONDecodeError) as error:
            entry = {"objectId": object_id, "timestamp": now(), "status": "FAILED", "errorType": type(error).__name__, "error": str(error)}
            if isinstance(error, AcquisitionNetworkError):
                entry.update(errorStage=error.stage, remoteHost=error.host, httpStatus=error.http_status)
                if hasattr(error, "api_error_code"):
                    entry["apiErrorCode"] = error.api_error_code
                consecutive_network_failures += 1
                if isinstance(error, RemoteCooldown):
                    entry["retryAfterSeconds"] = error.retry_after_seconds
            else:
                consecutive_network_failures = 0
            print(f"[{index}/{len(selected_ids)}] Met {object_id}: FAILED ({type(error).__name__}: {error})", file=sys.stderr, flush=True)
        results.append(entry)
        if args.execute:
            checkpoint["processed"][str(object_id)] = entry
            checkpoint["updatedAt"] = now()
            write_json_atomic(checkpoint_path, checkpoint)
            append_jsonl(audit_path, entry)
        if isinstance(entry.get("retryAfterSeconds"), (int, float)) or consecutive_network_failures >= 2:
            paused_for_network = True
            print("Pausing acquisition after remote failures; saved progress can be resumed later.", file=sys.stderr, flush=True)
            break
        time.sleep(args.pause_seconds)
    counts = {}
    for row in results:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    report = {"startedAt": now(), "input": str(source), "inputSha256": source_hash,
              "mode": "ATTACH" if args.attach else "ACQUIRE" if args.execute else "PREVIEW",
              "selectedObjects": len(selected_ids), "attemptedThisRun": len(results), "pausedForNetwork": paused_for_network,
              "excludedByCsvRights": excluded_rights, "counts": counts,
              "trainingDataApproved": False, "results": results}
    write_json_atomic(report_path, report)
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, indent=2), flush=True)
    return 1 if counts.get("FAILED") else 0


def main():
    try:
        return run(build_parser().parse_args())
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Met Commons acquisition failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
