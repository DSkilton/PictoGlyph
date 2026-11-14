import argparse
import csv
import hashlib
import json
import logging
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SOURCE_ID = "met-openaccess-metadata"
OBJECTS_URL = "https://collectionapi.metmuseum.org/public/collection/v1/objects"
CSV_URL = "https://media.githubusercontent.com/media/metmuseum/openaccess/master/MetObjects.csv"
PROVENANCE_LOGGER = logging.getLogger("PICTOGLYPH_PROVENANCE")
DEFAULT_ROOT = Path("/mnt/i/PictoGlyph/data/research-datasets/manifests/met-catalogue")
REQUIRED_COLUMNS = {"Object ID", "Title", "Object Name", "Is Public Domain", "Department"}
TEXT_FIELDS = ("Title", "Object Name", "Classification", "Tags", "Culture", "Period", "Dynasty")
META_FIELDS = ("Object ID", "Object Number", "Is Public Domain", "Department", "Object Name", "Title", "Culture",
               "Period", "Dynasty", "Object Date", "Object Begin Date", "Object End Date", "Medium", "Classification",
               "Geography Type", "Country", "Region", "Locale", "Locus", "Excavation", "Tags", "Link Resource", "Metadata Date")
STRONG = ("hieroglyph", "hieroglyphic", "hieratic", "demotic", "cuneiform", "inscription", "inscribed", "ostracon", "ostraka",
          "papyrus", "papyri", "glyph", "cartouche", "stela", "stelae", "stele", "writing", "script", "epigraph", "epigraphic",
          "linear a", "linear b", "maya", "mayan", "indus script", "proto-elamite", "proto-sinaitic", "coptic")
WEAK = ("seal", "scarab", "tablet", "relief", "fragment", "amulet", "plaque", "cylinder", "vessel")
RELEVANT_DEPARTMENTS = ("egyptian art", "ancient near eastern art", "greek and roman art", "islamic art",
                        "arts of africa, oceania, and the americas", "asian art")


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_json(value):
    payload = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def write_json_atomic(path, value):
    temp = path.with_name(path.name + ".tmp")
    with temp.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    temp.replace(path)


def get_bytes(url, max_bytes=25 * 1024 * 1024):
    for attempt in range(4):
        try:
            request = Request(url, headers={"User-Agent": "PictoGlyph-MSc-research/1.0", "Accept": "application/json"})
            with urlopen(request, timeout=60) as response:
                data = response.read(max_bytes + 1)
                if len(data) > max_bytes:
                    raise ValueError("The Met ID response exceeds the safety limit")
                return data
        except (HTTPError, URLError, TimeoutError) as error:
            code = getattr(error, "code", None)
            if code not in (None, 429, 500, 502, 503, 504) or attempt == 3:
                raise
            time.sleep(min(2 ** attempt, 8))
    raise RuntimeError("Unreachable retry state")


def current_ids(root, refresh=False):
    path = root / "met-object-ids.json"
    if path.exists() and not refresh:
        snapshot = json.loads(path.read_text(encoding="utf-8"))
    else:
        body = json.loads(get_bytes(OBJECTS_URL))
        raw_ids = body.get("objectIDs")
        if not isinstance(raw_ids, list) or not raw_ids:
            raise ValueError("The Met returned no object ID array")
        values = sorted({value for value in raw_ids if type(value) is int and value > 0})
        if len(values) != len(raw_ids) or body.get("total") != len(raw_ids):
            raise ValueError("Unexpected duplicate, invalid, or inconsistent Met object IDs")
        snapshot = {"schemaVersion": 1, "sourceId": SOURCE_ID, "sourceUrl": OBJECTS_URL,
                    "retrievedAt": timestamp(), "total": len(values), "objectIDs": values}
        write_json_atomic(path, snapshot)
    values = snapshot.get("objectIDs")
    if not isinstance(values, list) or not values or any(type(x) is not int or x <= 0 for x in values):
        raise ValueError("Saved Met object-ID snapshot is invalid")
    ids = set(values)
    if len(ids) != len(values) or snapshot.get("total") != len(values):
        raise ValueError("Saved Met object-ID snapshot is inconsistent")
    return ids, sha256_json(sorted(ids)), snapshot


def download_csv(path):
    if path.exists():
        print(f"Using existing CSV: {path}", flush=True)
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".part")
    request = Request(CSV_URL, headers={"User-Agent": "PictoGlyph-MSc-research/1.0"})
    try:
        with urlopen(request, timeout=90) as response, temp.open("wb") as output:
            total = 0
            next_report = 25 * 1024 * 1024
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > 600 * 1024 * 1024:
                    raise ValueError("Met CSV exceeds the configured 600 MiB limit")
                output.write(chunk)
                if total >= next_report:
                    print(f"CSV downloaded: {total // (1024 * 1024)} MiB", flush=True)
                    next_report += 25 * 1024 * 1024
        validate_csv(temp)
        temp.replace(path)
    except Exception:
        temp.unlink(missing_ok=True)
        raise


def validate_csv(path):
    if not path.is_file():
        raise FileNotFoundError(f"Met CSV not found: {path}. Use --download-csv or provide --csv PATH")
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        beginning = handle.read(100)
        if beginning.startswith("version https://git-lfs.github.com/spec/v1"):
            raise ValueError("Downloaded a Git LFS pointer, not MetObjects.csv")
        handle.seek(0)
        columns = set(csv.DictReader(handle).fieldnames or ())
    if not REQUIRED_COLUMNS.issubset(columns):
        raise ValueError(f"Met CSV missing columns: {sorted(REQUIRED_COLUMNS - columns)}")


def term_present(text, term):
    return re.search(r"(?<![a-z])" + re.escape(term) + r"(?![a-z])", text) is not None


def classify(row):
    text = " ".join((row.get(field) or "").lower() for field in TEXT_FIELDS)
    strong = [term for term in STRONG if term_present(text, term)]
    if strong:
        return "HIGH", strong
    department = (row.get("Department") or "").lower().strip()
    weak = [term for term in WEAK if term_present(text, term)] if department in RELEVANT_DEPARTMENTS else []
    return ("REVIEW", weak) if weak else (None, [])


def confirmed_public_domain(row):
    return (row.get("Is Public Domain") or "").strip().lower() in {"true", "yes", "1"}


def durable_checkpoint(checkpoint_path, checkpoint, candidates, covered):
    candidates.flush()
    covered.flush()
    os.fsync(candidates.fileno())
    os.fsync(covered.fileno())
    checkpoint["candidatesBytes"] = candidates.tell()
    checkpoint["coveredBytes"] = covered.tell()
    write_json_atomic(checkpoint_path, checkpoint)


def scan_csv(root, csv_path, ids, ids_hash, csv_hash, max_rows=None, checkpoint_interval=5000, reset=False):
    root.mkdir(parents=True, exist_ok=True)
    candidate_path = root / "met-candidates.jsonl"
    covered_path = root / "met-csv-covered-ids.txt"
    checkpoint_path = root / "scan-checkpoint.json"
    missing_path = root / "met-current-ids-not-in-csv.txt"
    report_path = root / "scan-report.json"
    rules_hash = sha256_json({"strong": STRONG, "weak": WEAK, "departments": RELEVANT_DEPARTMENTS, "fields": TEXT_FIELDS})

    if reset:
        for path in (candidate_path, covered_path, checkpoint_path, missing_path, report_path):
            path.unlink(missing_ok=True)
    identity = {"csvSha256": csv_hash, "idsSha256": ids_hash, "rulesSha256": rules_hash}
    state = {**identity, "processedRows": 0, "matched": 0, "publicDomainCandidates": 0,
             "nonPublicDomainCandidates": 0, "csvRowsOutsideCurrentApi": 0, "duplicateCsvIds": 0,
             "candidatesBytes": 0, "coveredBytes": 0, "finished": False, "startedAt": timestamp()}
    if checkpoint_path.exists():
        state = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        if any(state.get(key) != value for key, value in identity.items()):
            raise ValueError("Source snapshots or rules changed. Use --reset to start a new scan")
    elif candidate_path.exists() or covered_path.exists():
        raise ValueError("Output files exist without a checkpoint. Use --reset after inspecting them")

    for path, length_key in ((candidate_path, "candidatesBytes"), (covered_path, "coveredBytes")):
        if path.exists() and path.stat().st_size < state[length_key]:
            raise ValueError(f"Truncated checkpoint output: {path}")
        if not path.exists() and state[length_key]:
            raise ValueError(f"Missing checkpoint output: {path}")

    PROVENANCE_LOGGER.info("event=met_catalogue_scan_started csvSha256=%s idsSha256=%s resumedAtRow=%s", csv_hash, ids_hash, state["processedRows"])
    if not state["finished"]:
        with candidate_path.open("a+b") as candidates, covered_path.open("a+b") as covered:
            candidates.truncate(state["candidatesBytes"])
            covered.truncate(state["coveredBytes"])
            candidates.seek(0, 2)
            covered.seek(0, 2)
            with covered_path.open("r", encoding="ascii") as previous_ids:
                already_covered = {int(line.strip()) for line in previous_ids if line.strip()}
            csv.field_size_limit(10_000_000)
            with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
                reader = csv.DictReader(handle)
                for row_number, row in enumerate(reader, 1):
                    if row_number <= state["processedRows"]:
                        continue
                    if max_rows is not None and row_number > max_rows:
                        break
                    state["processedRows"] = row_number
                    raw_id = (row.get("Object ID") or "").strip()
                    if raw_id.isdecimal() and int(raw_id) in ids:
                        object_id = int(raw_id)
                        if object_id in already_covered:
                            state["duplicateCsvIds"] += 1
                            continue
                        already_covered.add(object_id)
                        covered.write(f"{object_id}\n".encode("ascii"))
                        priority, terms = classify(row)
                        if priority:
                            public_domain = confirmed_public_domain(row)
                            candidate = {"sourceId": SOURCE_ID, "objectId": object_id,
                                         "objectUrl": f"https://www.metmuseum.org/art/collection/search/{object_id}",
                                         "priority": priority, "matchedTerms": terms,
                                         "interpretationStatus": "UNVERIFIED_POTENTIAL_INSCRIPTION",
                                         "publicDomainReportedByCsv": public_domain,
                                         "imageAvailability": "NOT_YET_VERIFIED",
                                         "metadata": {key: row.get(key) for key in META_FIELDS if key in row},
                                         "provenance": {"catalogue": CSV_URL, "csvSha256": csv_hash,
                                                        "apiIdList": OBJECTS_URL, "idsSha256": ids_hash}}
                            candidates.write((json.dumps(candidate, ensure_ascii=False) + "\n").encode("utf-8"))
                            state["matched"] += 1
                            state["publicDomainCandidates" if public_domain else "nonPublicDomainCandidates"] += 1
                    else:
                        state["csvRowsOutsideCurrentApi"] += 1
                    if row_number % checkpoint_interval == 0:
                        durable_checkpoint(checkpoint_path, state, candidates, covered)
                        PROVENANCE_LOGGER.info("event=met_catalogue_scan_checkpoint scanned=%s candidates=%s", row_number, state["matched"])
                        print(f"Scanned {row_number:,} CSV rows; candidates {state['matched']:,}", flush=True)
                else:
                    state["finished"] = True
            durable_checkpoint(checkpoint_path, state, candidates, covered)

    else:
        for path, length_key in ((candidate_path, "candidatesBytes"), (covered_path, "coveredBytes")):
            if path.exists() and path.stat().st_size > state[length_key]:
                with path.open("r+b") as output:
                    output.truncate(state[length_key])
    covered_ids = set()
    if covered_path.exists():
        with covered_path.open("r", encoding="ascii") as handle:
            covered_ids = {int(line.strip()) for line in handle if line.strip()}
    missing_ids = sorted(ids - covered_ids) if state["finished"] else []
    if state["finished"]:
        missing_path.write_text("".join(f"{value}\n" for value in missing_ids), encoding="ascii")
    report = {"sourceId": SOURCE_ID, "completedAt": timestamp(), "status": "COMPLETE" if state["finished"] else "PARTIAL",
              "allCurrentApiObjectIds": len(ids), "csvRowsScanned": state["processedRows"],
              "currentApiIdsCoveredByCsv": len(covered_ids), "currentApiIdsMissingFromCsv": len(missing_ids) if state["finished"] else None,
              "candidateRecords": state["matched"], "publicDomainCandidates": state["publicDomainCandidates"],
              "nonPublicDomainCandidates": state["nonPublicDomainCandidates"],
              "csvRowsNotInCurrentApi": state["csvRowsOutsideCurrentApi"], "duplicateCsvIds": state.get("duplicateCsvIds", 0), **identity,
              "interpretation": "Keyword candidates only; images and sign labels are NOT verified; no PostgreSQL import performed"}
    write_json_atomic(report_path, report)
    PROVENANCE_LOGGER.info("event=met_catalogue_scan_%s scanned=%s candidates=%s coverage=%s", report["status"].lower(), state["processedRows"], state["matched"], len(covered_ids))
    return report


def main():
    parser = argparse.ArgumentParser(description="Enumerate all current Met IDs and scan the full Open Access CSV")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--csv", type=Path)
    parser.add_argument("--download-csv", action="store_true")
    parser.add_argument("--refresh-ids", action="store_true")
    parser.add_argument("--max-rows", type=int, help="Stop after N CSV records; rerun without this to resume")
    parser.add_argument("--reset", action="store_true", help="Discard scan outputs only; preserve source snapshots")
    args = parser.parse_args()
    if args.max_rows is not None and args.max_rows < 1:
        parser.error("--max-rows must be a positive integer")
    root = args.root.expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    PROVENANCE_LOGGER.setLevel(logging.INFO)
    handler = logging.FileHandler(root / "met-catalogue-provenance.log", encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
    PROVENANCE_LOGGER.addHandler(handler)
    csv_path = (args.csv or root / "MetObjects.csv").expanduser().resolve()
    try:
        if args.download_csv:
            download_csv(csv_path)
        validate_csv(csv_path)
        ids, ids_hash, snapshot = current_ids(root, refresh=args.refresh_ids)
        print(f"Current Met catalogue: {len(ids):,} IDs (captured {snapshot['retrievedAt']})", flush=True)
        print("Hashing official CSV before scan...", flush=True)
        report = scan_csv(root, csv_path, ids, ids_hash, sha256_file(csv_path), args.max_rows, reset=args.reset)
        print(json.dumps(report, indent=2), flush=True)
        print(f"Outputs: {root}", flush=True)
    except Exception:
        PROVENANCE_LOGGER.exception("event=met_catalogue_enumeration_failed")
        raise
    finally:
        handler.close()
        PROVENANCE_LOGGER.removeHandler(handler)


if __name__ == "__main__":
    main()
