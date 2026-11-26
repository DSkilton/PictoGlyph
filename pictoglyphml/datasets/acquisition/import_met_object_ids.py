"""Preview and optionally import exact Met object IDs through PictoGlyph's local API."""

import argparse
import json
import logging
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

from acquisition_io import append_jsonl, sha256_file, utc_now, write_json_atomic

from acquisition_constants import (
    COMPLETE, DEFAULT_API_BASE, FAILED, IMPORT_TOKEN_ENV, IMPORT_TOKEN_HEADER, JSON_CONTENT_TYPE,
    MET_MAX_BATCH_SIZE, MET_PUBLIC_DOMAIN_RIGHTS, MET_SOURCE_ID, PREVIEW_ONLY, PROVENANCE_LOGGER_NAME,
    SKIPPED_NO_IMAGE, SKIPPED_RIGHTS,
)

SOURCE_ID = MET_SOURCE_ID
API_PATH = f"/ingestion/research-discovery/{SOURCE_ID}"
PROVENANCE_LOGGER = logging.getLogger(PROVENANCE_LOGGER_NAME)
PUBLIC_DOMAIN_RIGHTS = MET_PUBLIC_DOMAIN_RIGHTS
IMAGE_HOST_PREFIX = "https://images.metmuseum.org/"
MAX_BATCH_SIZE = MET_MAX_BATCH_SIZE
MAX_RESPONSE_BYTES = 16 * 1024 * 1024
CHECKPOINT_VERSION = 1
IMPORTED = "IMPORTED"
RETRYABLE_SKIPS = {SKIPPED_RIGHTS, SKIPPED_NO_IMAGE}


class NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


def append_audit(path, event, **details):
    record = {"timestamp": utc_now(), "sourceId": SOURCE_ID, "event": event, **details}
    
    append_jsonl(path, record)

    PROVENANCE_LOGGER.info("event=%s %s", event, json.dumps(details, sort_keys=True))


def load_candidates(path):
    if not path.is_file():
        raise FileNotFoundError(f"Met shortlist not found: {path}")
        
    object_ids = []
    seen_ids = set()
    skipped_csv_rights = 0
    
    with path.open("r", encoding="utf-8") as stream:
        for line_number, raw_line in enumerate(stream, 1):
            if not raw_line.strip():
                continue
                
            try:
                record = json.loads(raw_line)
            except json.JSONDecodeError as exception:
                raise ValueError(f"Invalid JSON at line {line_number}") from exception
            
            if not isinstance(record, dict):
                raise ValueError(f"Expected a JSON object at line {line_number}")
            object_id = record.get("objectId")
            
            if type(object_id) is not int or object_id <= 0:
                raise ValueError(f"Invalid objectId at line {line_number}")
            
            if object_id in seen_ids:
                raise ValueError(f"Duplicate objectId {object_id} at line {line_number}")
            
            if record.get("sourceId", SOURCE_ID) != SOURCE_ID:
                raise ValueError(f"Wrong sourceId at line {line_number}")
            csv_rights = record.get("publicDomainReportedByCsv")
            
            if csv_rights is not None and type(csv_rights) is not bool:
                raise ValueError(f"Invalid publicDomainReportedByCsv at line {line_number}")
            
            seen_ids.add(object_id)
            
            if csv_rights is False:
                skipped_csv_rights += 1
                continue
            
            object_ids.append(object_id)
    
    if not object_ids:
        raise ValueError("The shortlist has no eligible object IDs")
    
    return object_ids, skipped_csv_rights


def validate_api_base(api_base):
    parsed = urlsplit(api_base)
    
    if (parsed.scheme not in ("http", "https") or parsed.hostname not in ("127.0.0.1", "localhost", "::1")
            or parsed.username is not None or parsed.password is not None or parsed.path not in ("", "/")
            or parsed.query or parsed.fragment or parsed.port == 0):
        
        raise ValueError("Only a local PictoGlyph API origin is supported")
    
    return api_base.rstrip("/")


def post_json(api_base, operation, object_ids, token, timeout):
    url = f"{api_base}{API_PATH}/{operation}"
    request = Request(url, data=json.dumps(object_ids).encode("utf-8"), method="POST", headers={
        "Content-Type": JSON_CONTENT_TYPE, "Accept": JSON_CONTENT_TYPE,
        IMPORT_TOKEN_HEADER: token
    })
    
    try:
        with build_opener(ProxyHandler({}), NoRedirectHandler()).open(request, timeout=timeout) as response:
            payload = response.read(MAX_RESPONSE_BYTES + 1)
            
            if len(payload) > MAX_RESPONSE_BYTES:
                raise ValueError("The PictoGlyph API response exceeded the size limit")
            
            result = json.loads(payload)
            
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object from the PictoGlyph API")
            
            return result
    
    except HTTPError as exception:
        detail = exception.read(500).decode("utf-8", errors="replace")
        raise RuntimeError(f"{operation} returned HTTP {exception.code}: {detail}") from exception
    
    except URLError as exception:
        raise RuntimeError(f"Cannot reach the local PictoGlyph API during {operation}: {exception.reason}") from exception


def assess_preview(response, requested_ids):
    if response.get("sourceId") != SOURCE_ID:
        raise ValueError("Preview sourceId does not match the requested Met source")
    
    evidence = response.get("rawResponse")
    
    if not isinstance(evidence, dict) or evidence.get("retrievalMethod") != "EXACT_OBJECT_ID":
        raise ValueError("Preview is missing exact-ID retrieval evidence")
    if evidence.get("requestedObjectIds") != requested_ids:
        raise ValueError("Preview requestedObjectIds do not match the batch")
  
    candidates = response.get("candidates")
    
    if not isinstance(candidates, list) or len(candidates) != len(requested_ids):
        raise ValueError("Preview returned an unexpected number of candidates")

    outcomes = {}
    
    for object_id, candidate in zip(requested_ids, candidates):
        if not isinstance(candidate, dict) or candidate.get("sourceRecordId") != str(object_id):
            raise ValueError(f"Preview returned an unexpected record for ID {object_id}")
    
        raw_record = candidate.get("rawRecord")
        
        if not isinstance(raw_record, dict) or type(raw_record.get("objectID")) is not int or raw_record["objectID"] != object_id:
            raise ValueError(f"Preview raw museum record does not match ID {object_id}")
        
        images = candidate.get("imageUrls")
        
        if not isinstance(images, list) or any(not isinstance(url, str) or not url.startswith(IMAGE_HOST_PREFIX) for url in images):
            raise ValueError(f"Preview contains invalid image references for ID {object_id}")
        
        if raw_record.get("isPublicDomain") is not True or candidate.get("rights") != PUBLIC_DOMAIN_RIGHTS:
            status = SKIPPED_RIGHTS
        elif not images:
            status = SKIPPED_NO_IMAGE
        else:
            status = "READY_FOR_IMPORT"
        
        outcomes[object_id] = {"status": status, "title": candidate.get("title", ""), "imageReferences": len(images)}
        
    return outcomes


def verify_import(response, expected_ids):
    if response.get("sourceId") != SOURCE_ID or response.get("candidatesFound") != len(expected_ids):
        raise ValueError("Import response has an unexpected source or candidate count")
    
    counts = ("recordsCreated", "recordsReused", "imageReferencesCreated", "skippedWithoutIdentity")
    
    if any(type(response.get(key)) is not int or response[key] < 0 for key in counts):
        raise ValueError("Import response has invalid counters")
    
    if response["recordsCreated"] + response["recordsReused"] != len(expected_ids) or response["skippedWithoutIdentity"] != 0:
        raise ValueError("Import did not account for every requested object")
        
    return response


def load_checkpoint(path, input_hash, api_base):
    expected = {"schemaVersion": CHECKPOINT_VERSION, "sourceId": SOURCE_ID,
                "inputSha256": input_hash, "apiBase": api_base}
    
    if not path.exists():
        return {**expected, "processed": {}, "updatedAt": utc_now()}
    
    checkpoint = json.loads(path.read_text(encoding="utf-8"))
    
    if any(checkpoint.get(key) != value for key, value in expected.items()):
        raise ValueError("Checkpoint belongs to a different shortlist or API. Use a different --output-dir")
    
    processed = checkpoint.get("processed")
    
    if not isinstance(processed, dict) or any(not str(key).isdecimal() or value not in (IMPORTED, *RETRYABLE_SKIPS)
                                                  for key, value in processed.items()):
        raise ValueError("Checkpoint contains invalid processed IDs")
    
    return checkpoint


def chunks(values, size):
    for index in range(0, len(values), size):
        yield values[index:index + size]


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Curated Met JSONL shortlist (objectId per line)")
    parser.add_argument("--output-dir", type=Path, help="Reports, provenance log and resumable checkpoint")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--batch-size", type=int, default=5)
    parser.add_argument("--max-objects", type=int, default=5, help="Maximum shortlisted objects for this run")
    parser.add_argument("--timeout", type=float, default=45.0)
    parser.add_argument("--pause-seconds", type=float, default=0.25)
    parser.add_argument("--execute", action="store_true", help="Import approved candidates into PostgreSQL")
    parser.add_argument("--recheck-skipped", action="store_true", help="Revisit objects previously skipped for rights or images")
    return parser


def run(args):
    
    if not 1 <= args.batch_size <= MAX_BATCH_SIZE or args.max_objects < 1 or args.timeout <= 0 or args.pause_seconds < 0:
        raise ValueError(f"Batch size 1-{MAX_BATCH_SIZE}, positive limits/timeouts and non-negative pauses required")
    
    if args.recheck_skipped and not args.execute:
        raise ValueError("--recheck-skipped requires --execute")
    api_base = validate_api_base(args.api_base)
    token = os.environ.get(IMPORT_TOKEN_ENV, "")
    
    if not token:
        raise ValueError("Set PICTOGLYPH_RESEARCH_IMPORT_TOKEN before calling the protected API")
    
    source = args.input.expanduser().resolve()
    ids, csv_skipped = load_candidates(source)
    ids = ids[:args.max_objects]
    input_hash = sha256_file(source)
    output_dir = (args.output_dir or source.parent / f"{source.stem}-exact-id-run").expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    audit_path = output_dir / "met-exact-id-audit.jsonl"
    checkpoint_path = output_dir / "met-exact-id-checkpoint.json"
    report_path = output_dir / "met-exact-id-report.json"
    checkpoint = load_checkpoint(checkpoint_path, input_hash, api_base) if args.execute else None
    previous = checkpoint["processed"] if checkpoint else {}
    completed_ids = {int(key) for key, status in previous.items()
                     if status == IMPORTED or (status in RETRYABLE_SKIPS and not args.recheck_skipped)}
    pending = [object_id for object_id in ids if object_id not in completed_ids]

    report = {"sourceId": SOURCE_ID, "startedAt": utc_now(), "mode": "EXECUTE" if args.execute else PREVIEW_ONLY,
              "input": str(source), "inputSha256": input_hash, "selectedObjects": len(ids),
              "excludedByCsvRights": csv_skipped, "previouslyProcessed": len(ids) - len(pending),
              "previewedThisRun": 0, "readyThisRun": 0, "importedThisRun": 0,
              "skippedRightsThisRun": 0, "skippedNoImageThisRun": 0,
              "recordsCreated": 0, "recordsReused": 0, "imageReferencesCreated": 0,
              "trainingDataApproved": False, "status": "RUNNING"}
    append_audit(audit_path, "met_exact_id_run_started", mode=report["mode"], inputSha256=input_hash,
                 selected=len(ids), pending=len(pending))
    batches = list(chunks(pending, args.batch_size))
    
    try:
        for batch_number, batch in enumerate(batches, 1):
            preview = post_json(api_base, "preview-ids", batch, token, args.timeout)
            outcomes = assess_preview(preview, batch)
            ready = [object_id for object_id in batch if outcomes[object_id]["status"] == "READY_FOR_IMPORT"]
            skipped_rights = sum(outcomes[value]["status"] == SKIPPED_RIGHTS for value in batch)
            skipped_images = sum(outcomes[value]["status"] == SKIPPED_NO_IMAGE for value in batch)
            report["previewedThisRun"] += len(batch)
            report["readyThisRun"] += len(ready)
            report["skippedRightsThisRun"] += skipped_rights
            report["skippedNoImageThisRun"] += skipped_images
            append_audit(audit_path, "met_exact_id_batch_previewed", batch=batch_number, objectIds=batch, outcomes=outcomes)

            if args.execute:
                if ready:
                    result = verify_import(post_json(api_base, "import-ids", ready, token, args.timeout), ready)
                    report["importedThisRun"] += len(ready)
                    for source_key, report_key in (("recordsCreated", "recordsCreated"), ("recordsReused", "recordsReused"),
                                                   ("imageReferencesCreated", "imageReferencesCreated")):
                        report[report_key] += result[source_key]
                    append_audit(audit_path, "met_exact_id_batch_imported", batch=batch_number, objectIds=ready,
                                 created=result["recordsCreated"], reused=result["recordsReused"],
                                 imageReferencesCreated=result["imageReferencesCreated"])
                    
                for object_id in batch:
                    checkpoint["processed"][str(object_id)] = IMPORTED if object_id in ready else outcomes[object_id]["status"]
                checkpoint["updatedAt"] = utc_now()
                write_json_atomic(checkpoint_path, checkpoint)

            print(f"Batch {batch_number}/{len(batches)}: previewed={len(batch)} ready={len(ready)} "
                  f"skippedRights={skipped_rights} noImages={skipped_images} "
                  f"imported={len(ready) if args.execute else 0}", flush=True)
            
            if batch_number < len(batches) and args.pause_seconds:
                time.sleep(args.pause_seconds)
        
        report["status"] = COMPLETE
    
    except Exception as exception:
        report["status"] = FAILED
        report["errorType"] = type(exception).__name__
        report["error"] = str(exception)
        append_audit(audit_path, "met_exact_id_run_failed", errorType=type(exception).__name__)
        raise
    
    finally:
        report["completedAt"] = utc_now()
        write_json_atomic(report_path, report)
        print(json.dumps(report, indent=2), flush=True)
    
    append_audit(audit_path, "met_exact_id_run_completed", imported=report["importedThisRun"], status=report["status"])
    
    return report


def main():
    parser = build_parser()
    args = parser.parse_args()
    try:
        run(args)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
