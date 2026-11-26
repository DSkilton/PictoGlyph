import json
from pathlib import Path

def main():
    here = Path(__file__).resolve().parent
    original_path = here / "sources.json"
    supplement_path = here / "sources_institutional_v2.json"
    output_path = here / "sources_extended_v2.json"

    original = json.loads(original_path.read_text(encoding="utf-8"))
    supplement = json.loads(supplement_path.read_text(encoding="utf-8"))

    if original.get("schema_version") != 2 or supplement.get("schema_version") != 2:
        raise ValueError("Both catalogues must have schema_version 2")

    sources = [dict(source) for source in original["sources"]]
    by_id = {item["id"]: item for item in sources}

    if len(by_id) != len(sources):
        raise ValueError("Duplicate IDs in the original source catalogue")

    for item in supplement["sources"]:
        if item["id"] in by_id:
            raise ValueError(f"New institutional source already exists: {item['id']}")
        sources.append(item)
        by_id[item["id"]] = item

    for update in supplement.get("source_updates", []):
        target = by_id.get(update["id"])
        if target is None:
            raise ValueError(f"Source update refers to an unknown ID: {update['id']}")

        for field in ("java_preview_support", "adapter", "api_url"):
            if field in update:
                target[field] = update[field]

    result = dict(original)
    result["sources"] = sources
    result["source_count"] = len(sources)
    result["description"] = original.get("description", "") + " Institutional sources appended; new sources are review-only."
    output_path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(sources)} sources to {output_path}; original sources.json unchanged")


if __name__ == "__main__":
    main()
