import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

TESTS = [
    ("met-openaccess-metadata", "hieroglyph", 2),
    ("cleveland-museum", "seal", 2),
    ("louvre-collections", "cl010277627", 1),
    ("cdli-live", "P000001", 1),
    ("opencontext-archaeology", "inscription", 2),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("/mnt/i/PictoGlyph/data/research-datasets/manifests/api-preview"))
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    parts = urllib.parse.urlsplit(args.api_url)
    if parts.scheme != "http" or parts.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Only a local HTTP research API is supported")
    token = os.environ.get("PICTOGLYPH_RESEARCH_IMPORT_TOKEN")
    if not token:
        parser.error("PICTOGLYPH_RESEARCH_IMPORT_TOKEN must be exported")
    args.output.mkdir(parents=True, exist_ok=True)
    failures = 0
    print(f"{'SOURCE':<30} {'RECORDS':>8} {'IMAGES':>8} {'SECONDS':>9}  STATUS", flush=True)
    for source, query, limit in TESTS:
        url = args.api_url.rstrip("/") + f"/ingestion/research-discovery/{source}/preview?" + urllib.parse.urlencode({"q": query, "limit": limit})
        request = urllib.request.Request(url, headers={"X-PictoGlyph-Import-Token": token, "Accept": "application/json"})
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                result = json.load(response)
            (args.output / f"{source}.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            candidates = result.get("candidates", [])
            images = sum(len(item.get("imageUrls", [])) for item in candidates)
            print(f"{source:<30} {len(candidates):>8} {images:>8} {time.monotonic() - started:>9.1f}  OK", flush=True)
        except (urllib.error.URLError, OSError, ValueError, json.JSONDecodeError) as exc:
            failures += 1
            print(f"{source:<30} {'-':>8} {'-':>8} {time.monotonic() - started:>9.1f}  {type(exc).__name__}: {str(exc)[:90]}", flush=True)
    print(f"Results: {len(TESTS) - failures}/{len(TESTS)} API previews successful. Raw source metadata saved to {args.output}.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
