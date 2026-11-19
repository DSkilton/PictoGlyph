import argparse
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener

from import_met_object_ids import (NoRedirectHandler, SOURCE_ID, append_audit, assess_preview,
                                   load_candidates, post_json, sha256_file, utc_now, validate_api_base,
                                   write_json_atomic)

DEFAULT_API_BASE = "http://127.0.0.1:8080"
IMAGE_HOST = "images.metmuseum.org"
RIGHTS_STATEMENT = "PUBLIC_DOMAIN_REPORTED_BY_SOURCE"
MAX_BATCH_SIZE = 10
MAX_IMAGES_PER_OBJECT = 20
DEFAULT_MAX_IMAGE_BYTES = 25 * 1024 * 1024
CHUNK_BYTES = 1024 * 1024
JPEG_CONTENT_TYPES = {"image/jpeg", "image/jpg"}
SCHEMA_VERSION = 1


def validate_image_url(url):
    if not isinstance(url, str):
        raise ValueError("Met image URL must be a string")

    try:
        parsed = urlsplit(url)
        valid = (parsed.scheme == "https" and parsed.hostname == IMAGE_HOST and parsed.port is None
                 and parsed.username is None and parsed.password is None and parsed.path.startswith("/")
                 and parsed.path != "/" and not parsed.fragment and not parsed.query)

    except ValueError as exception:
        raise ValueError("Invalid Met image URL") from exception

    if not valid:
        raise ValueError("Image URL must be HTTPS on the authorised Met image host")

    return url


def image_paths(output_dir, object_id, url):
    url_digest = hashlib.sha256(url.encode("utf-8")).hexdigest()
    object_dir = output_dir / "images" / SOURCE_ID / str(object_id)

    return object_dir / f"{url_digest}.jpg", object_dir / f"{url_digest}.json"


def verify_existing(image_path, metadata_path, object_id, url):
    if image_path.is_symlink() or metadata_path.is_symlink():
        raise ValueError(f"Refusing to follow an existing symlink: {image_path}")

    if not image_path.exists() and not metadata_path.exists():
        return None

    if not image_path.is_file() or not metadata_path.is_file():
        raise ValueError(f"Incomplete image/metadata pair requires review: {image_path}")

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))

    if (metadata.get("schemaVersion") != SCHEMA_VERSION or metadata.get("sourceId") != SOURCE_ID
            or metadata.get("objectId") != object_id or metadata.get("sourceUrl") != url
            or metadata.get("rights") != RIGHTS_STATEMENT or metadata.get("trainingDataApproved") is not False
            or metadata.get("verificationStatus") != "JPEG_SIGNATURE_ONLY"):
        raise ValueError(f"Local acquisition metadata disagrees with the source: {metadata_path}")

    if (metadata.get("fileSize") != image_path.stat().st_size
            or metadata.get("sha256") != sha256_file(image_path)):
        raise ValueError(f"Existing image checksum or size differs: {image_path}")

    return metadata


def fetch_jpeg(url, destination, max_bytes, timeout):
    validate_image_url(url)
    request = Request(url, headers={"User-Agent": "PictoGlyph-MSc-image-acquisition/1.0", "Accept": "image/jpeg"})
    opener = build_opener(ProxyHandler({}), NoRedirectHandler())
    temporary_path = None

    try:
        with opener.open(request, timeout=timeout) as response:
            content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()

            if content_type not in JPEG_CONTENT_TYPES:
                raise ValueError(f"Unexpected Met image content type: {content_type}")

            size_header = response.headers.get("Content-Length")

            if size_header is not None and int(size_header) > max_bytes:
                raise ValueError("Met image exceeds the configured size limit")

            destination.parent.mkdir(parents=True, exist_ok=True)
            digest = hashlib.sha256()
            total = 0
            first_bytes = b""
            last_bytes = b""

            with tempfile.NamedTemporaryFile(mode="wb", prefix=".met-", suffix=".part", dir=destination.parent, delete=False) as temporary:
                temporary_path = Path(temporary.name)

                while chunk := response.read(CHUNK_BYTES):
                    total += len(chunk)

                    if total > max_bytes:
                        raise ValueError("Met image exceeds the configured size limit")

                    if len(first_bytes) < 3:
                        first_bytes += chunk[:3 - len(first_bytes)]
                    last_bytes = (last_bytes + chunk)[-2:]
                    digest.update(chunk)
                    temporary.write(chunk)
                temporary.flush()
                os.fsync(temporary.fileno())

            if total < 5 or first_bytes != b"\xff\xd8\xff" or last_bytes != b"\xff\xd9":
                raise ValueError("Downloaded content is not a complete JPEG by signature")

            if destination.exists() or destination.is_symlink():
                raise ValueError(f"Image appeared during download; refusing overwrite: {destination}")

            temporary_path.replace(destination)
            temporary_path = None

            return {"sha256": digest.hexdigest(), "fileSize": total}

    except (HTTPError, URLError) as exception:
        raise RuntimeError(f"Unable to download Met image: {type(exception).__name__}") from exception

    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def acquire_image(output_dir, object_id, url, raw_record, timeout, max_bytes):
    validate_image_url(url)
    image_path, metadata_path = image_paths(output_dir, object_id, url)

    if image_path.parent.is_symlink():
        raise ValueError(f"Image directory is a symlink: {image_path.parent}")

    previous = verify_existing(image_path, metadata_path, object_id, url)

    if previous is not None:
        return "REUSED", previous

    downloaded = fetch_jpeg(url, image_path, max_bytes, timeout)
    metadata = {"schemaVersion": SCHEMA_VERSION, "sourceId": SOURCE_ID, "objectId": object_id,
                "sourceUrl": url, "localPath": str(image_path), "sha256": downloaded["sha256"],
                "fileSize": downloaded["fileSize"], "rights": RIGHTS_STATEMENT,
                "accessionNumber": raw_record.get("accessionNumber"),
                "sourceMetadataDate": raw_record.get("metadataDate"), "retrievedAt": utc_now(),
                "verificationStatus": "JPEG_SIGNATURE_ONLY", "verifiedSignLabel": None,
                "trainingDataApproved": False, "linkedToPostgreSql": False}
    write_json_atomic(metadata_path, metadata)

    return "DOWNLOADED", metadata


def chunks(items, batch_size):
    for start in range(0, len(items), batch_size):
        yield items[start:start + batch_size]


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Met catalogue JSONL shortlist")
    parser.add_argument("--output-dir", type=Path, required=True, help="Image acquisition root outside the Git repository")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--max-objects", type=int, default=2)
    parser.add_argument("--max-images-per-object", type=int, default=3)
    parser.add_argument("--max-image-mb", type=int, default=25)
    parser.add_argument("--timeout", type=float, default=45.0)
    parser.add_argument("--execute", action="store_true", help="Download museum JPEGs; default is read-only preview")
    return parser


def run(args):
    if not 1 <= args.batch_size <= MAX_BATCH_SIZE or args.max_objects <= 0:
        raise ValueError("Batch size must be 1-10 and max objects must be positive")

    if not 1 <= args.max_images_per_object <= MAX_IMAGES_PER_OBJECT or not 1 <= args.max_image_mb <= 100:
        raise ValueError("Image count must be 1-20 and image size must be 1-100 MiB")

    if args.timeout <= 0:
        raise ValueError("Timeout must be positive")

    api_base = validate_api_base(args.api_base)
    token = os.environ.get("PICTOGLYPH_RESEARCH_IMPORT_TOKEN", "")

    if not token:
        raise ValueError("Set PICTOGLYPH_RESEARCH_IMPORT_TOKEN before calling the protected API")

    shortlist = args.input.expanduser().resolve(strict=True)
    object_ids, skipped_csv_rights = load_candidates(shortlist)
    object_ids = object_ids[:args.max_objects]
    requested_root = args.output_dir.expanduser()

    if requested_root.is_symlink():
        raise ValueError("The image output directory must not be a symlink")

    root = requested_root.resolve()
    root.mkdir(parents=True, exist_ok=True)
    audit_path = root / "met-image-acquisition-audit.jsonl"
    report_path = root / "met-image-acquisition-last-run.json"
    report = {"sourceId": SOURCE_ID, "startedAt": utc_now(),
              "mode": "EXECUTE" if args.execute else "PREVIEW_ONLY", "input": str(shortlist),
              "inputSha256": sha256_file(shortlist), "selectedObjects": len(object_ids),
              "excludedByCsvRights": skipped_csv_rights, "previewedObjects": 0, "eligibleObjects": 0,
              "skippedRights": 0, "skippedNoImage": 0, "imageReferences": 0,
              "imagesSelected": 0, "imagesDownloaded": 0, "imagesReused": 0,
              "imagesTruncated": 0, "trainingDataApproved": False, "databaseUpdated": False,
              "status": "RUNNING"}
    append_audit(audit_path, "met_image_run_started", mode=report["mode"], inputSha256=report["inputSha256"], objectIds=object_ids)

    try:
        for batch_number, batch in enumerate(chunks(object_ids, args.batch_size), 1):
            response = post_json(api_base, "preview-ids", batch, token, args.timeout)
            outcomes = assess_preview(response, batch)
            append_audit(audit_path, "met_image_batch_previewed", batch=batch_number, objectIds=batch, outcomes=outcomes)
            report["previewedObjects"] += len(batch)

            for object_id, candidate in zip(batch, response["candidates"]):
                outcome = outcomes[object_id]["status"]

                if outcome == "SKIPPED_RIGHTS":
                    report["skippedRights"] += 1
                    append_audit(audit_path, "met_image_object_skipped", objectId=object_id, reason="RIGHTS")
                    continue

                if outcome == "SKIPPED_NO_IMAGE":
                    report["skippedNoImage"] += 1
                    append_audit(audit_path, "met_image_object_skipped", objectId=object_id, reason="NO_IMAGE")
                    continue

                report["eligibleObjects"] += 1
                urls = candidate["imageUrls"]
                report["imageReferences"] += len(urls)
                report["imagesTruncated"] += max(0, len(urls) - args.max_images_per_object)

                for url in urls[:args.max_images_per_object]:
                    validate_image_url(url)
                    report["imagesSelected"] += 1

                    if not args.execute:
                        continue

                    action, metadata = acquire_image(root, object_id, url, candidate["rawRecord"], args.timeout,
                                                     args.max_image_mb * 1024 * 1024)
                    report["imagesDownloaded" if action == "DOWNLOADED" else "imagesReused"] += 1
                    append_audit(audit_path, "met_image_" + action.lower(), objectId=object_id,
                                 url=url, localPath=metadata["localPath"], sha256=metadata["sha256"],
                                 fileSize=metadata["fileSize"], rights=metadata["rights"])

            print(f"Batch {batch_number}: previewed={len(batch)} eligible={sum(outcomes[x]['status'] == 'READY_FOR_IMPORT' for x in batch)} "
                  f"downloaded={report['imagesDownloaded']} reused={report['imagesReused']}", flush=True)

        report["status"] = "COMPLETE"

    except Exception as exception:
        report["status"] = "FAILED"
        report["errorType"] = type(exception).__name__
        report["error"] = str(exception)
        append_audit(audit_path, "met_image_run_failed", errorType=type(exception).__name__, message=str(exception))
        raise

    finally:
        report["completedAt"] = utc_now()
        write_json_atomic(report_path, report)
        append_audit(audit_path, "met_image_run_finished", status=report["status"], imagesDownloaded=report["imagesDownloaded"],
                     imagesReused=report["imagesReused"], inputSha256=report["inputSha256"])
        print(json.dumps(report, indent=2), flush=True)

    return report


def main():
    try:
        run(build_parser().parse_args())

    except (ValueError, FileNotFoundError, OSError, RuntimeError, json.JSONDecodeError) as exception:
        print(f"Met image acquisition failed: {exception}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
