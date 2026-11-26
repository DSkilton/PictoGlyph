import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from acquisition_constants import (DEFAULT_API_BASE, DEFAULT_DATA_ROOT, IMPORT_TOKEN_ENV, IMPORT_TOKEN_HEADER, JSON_CONTENT_TYPE)

DEFAULT_OUTPUT = DEFAULT_DATA_ROOT / "manifests" / "api-preview"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--q", required=True)
    parser.add_argument("--limit", type=int, default=2)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--api-url", default=DEFAULT_API_BASE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--execute", action="store_true", help="Explicitly POST discovered records to PostgreSQL")
    args = parser.parse_args()
    uri = urllib.parse.urlsplit(args.api_url)
    if uri.scheme != "http" or uri.hostname not in ("127.0.0.1", "localhost", "::1") or uri.username or uri.password:
        parser.error("Only local HTTP research APIs are allowed")

    if not args.source_id or not all(c.isalnum() or c == "-" for c in args.source_id):
        parser.error("--source-id must be alphanumeric or hyphens")

    if not 1 <= args.limit <= 20 or not 0 <= args.offset <= 9980 or not 1 <= len(args.q) <= 80:
        parser.error("Require limit 1-20, offset 0-9980, query 1-80 characters")

    token = os.environ.get(IMPORT_TOKEN_ENV)

    if not token:
        parser.error("PICTOGLYPH_RESEARCH_IMPORT_TOKEN must be exported")

    query = urllib.parse.urlencode({"q": args.q, "limit": args.limit, "offset": args.offset})
    endpoint = args.api_url.rstrip("/") + "/ingestion/research-discovery/" + args.source_id
    headers = {IMPORT_TOKEN_HEADER: token, "Accept": JSON_CONTENT_TYPE}


    def call(path: str, method: str):
        request = urllib.request.Request(endpoint + path + "?" + query, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.status, json.load(response)

    started = time.monotonic()
    args.output.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    try:
        code, preview = call("/preview", "GET")
        path = args.output / f"{args.source_id}-{stamp}-preview.json"
        path.write_text(json.dumps(preview, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        candidates = preview.get("candidates", [])
        images = sum(len(row.get("imageUrls") or []) for row in candidates)

        print(f"Preview: HTTP {code}; candidates={len(candidates)}, remote image references={images}", flush=True)
        print(f"Saved: {path}", flush=True)

        if args.execute:
            code, result = call("/import", "POST")
            result_path = args.output / f"{args.source_id}-{stamp}-import.json"
            result_path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"Import: HTTP {code} | {json.dumps(result)}", flush=True)

        else:
            print("DRY RUN: no records written. Add --execute only after reviewing the preview.", flush=True)
        print(f"Elapsed: {time.monotonic() - started:.1f}s", flush=True)

        return 0
    except urllib.error.HTTPError as error:
        print(f"HTTP {error.code}: {error.read(1000).decode('utf-8', errors='replace')}", flush=True)
    except (OSError, urllib.error.URLError, ValueError) as error:
        print(f"FAILED: {type(error).__name__}: {error}", flush=True)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
