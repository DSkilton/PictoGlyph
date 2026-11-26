import hashlib
import json
import os
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from acquisition_constants import (CC0_LICENCE, DEFAULT_API_BASE, DOWNLOADED_LOCAL, IMPORT_TOKEN_ENV, IMPORT_TOKEN_HEADER, JPEG_START, JSON_CONTENT_TYPE, LOCAL_RESEARCH_IMAGE, MET_SOURCE_ID)

PILOT = Path(os.environ["MET_PILOT"])
API_BASE = DEFAULT_API_BASE
TOKEN = os.environ[IMPORT_TOKEN_ENV]
EXECUTE = "--execute" in sys.argv

OBJECTS = [
    {
        "id": 550876,
        "sha256": "eb4eeed869fee5050f1c3a21e0d4f8eff50cb7dbdeff8e0cedac4430868a426a",
        "filePage": "https://commons.wikimedia.org/wiki/File:Inlay,_hieroglyph_MET_26-3-164i.jpg",
        "downloadUrl": "https://upload.wikimedia.org/wikipedia/commons/4/44/Inlay%2C_hieroglyph_MET_26-3-164i.jpg"
    },
    {
        "id": 550878,
        "sha256": "0a7f0827bf7a94c3d489c5658511841a69810b985ba46d2148e0f70c9cad3242",
        "filePage": "https://commons.wikimedia.org/wiki/File:Inlay,_hieroglyph_MET_26-3-164b.jpg",
        "downloadUrl": "https://upload.wikimedia.org/wikipedia/commons/d/da/Inlay%2C_hieroglyph_MET_26-3-164b.jpg"
    }
]


def prepare_attachment(obj):
    image_path = PILOT / "commons-pilot" / f"met-{obj['id']}.jpg"
    data = image_path.read_bytes()

    if not data.startswith(JPEG_START):
        raise ValueError(f"Object {obj['id']} is not a JPEG")

    digest = hashlib.sha256(data).hexdigest()

    if digest != obj["sha256"]:
        raise ValueError(f"Object {obj['id']} failed SHA-256 verification")

    return {
        "assetKey": f"commons:{obj['id']}",
        "sourcePath": str(image_path.resolve()),
        "imageKind": LOCAL_RESEARCH_IMAGE,
        "rights": CC0_LICENCE,
        "checksumSha256": digest,
        "analysis": {
            "distributionSource": "Wikimedia Commons",
            "distributionFilePage": obj["filePage"],
            "distributionUrl": obj["downloadUrl"],
            "museumSource": "The Metropolitan Museum of Art",
            "metObjectId": obj["id"],
            "fileSizeBytes": len(data),
            "downloadStatus": DOWNLOADED_LOCAL,
            "checksumVerification": "MATCHED_LOCAL_PILOT_DIGEST",
            "trainingDataApproved": False
        }
    }


def attach(obj, payload):
    endpoint = f"{API_BASE}/ingestion/research-records/{MET_SOURCE_ID}/records/object:{obj['id']}/image-assets"
    request = Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": JSON_CONTENT_TYPE,
            IMPORT_TOKEN_HEADER: TOKEN
        },
        method="POST"
    )

    try:
        with urlopen(request, timeout=30) as response:
            print(f"Object {obj['id']}: HTTP {response.status}")
            print(response.read().decode("utf-8"))
    except HTTPError as error:
        print(f"Object {obj['id']}: HTTP {error.code}")
        print(error.read().decode("utf-8"))
        raise


def main():
    attachments = []

    for obj in OBJECTS:
        payload = prepare_attachment(obj)
        attachments.append((obj, payload))
        print(f"Verified object {obj['id']}: {payload['checksumSha256']}")

    if not EXECUTE:
        print("PREVIEW_ONLY: Both images verified. No database changes.")
        return

    for obj, payload in attachments:
        attach(obj, payload)


if __name__ == "__main__":
    main()
