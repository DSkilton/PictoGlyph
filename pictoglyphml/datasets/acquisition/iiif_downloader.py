
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

import requests


DATA_ROOT = Path("/mnt/i/PictoGlyph/data/research-datasets/iiif")

DOWNLOAD_PREVIEWS = True
SHOW_CANVASES = False
CHECK_IMAGE_SERVICE = True
DOWNLOAD_IMAGES = False
DOWNLOAD_MAX_IMAGE = False


def load_manifest(manifest_url: str, data_dir: Path) -> dict:
    manifest_path = data_dir / "manifest.json"
    source_path = data_dir / "manifest-url.txt"

    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

        cached_url = (
            source_path.read_text(encoding="utf-8").strip()
            if source_path.exists()
            else manifest.get("id")
        )

        if cached_url != manifest_url:
            raise ValueError(
                f"Source directory already contains a different manifest: {cached_url!r}. "
                "Choose a new --source-id instead of reusing this directory."
            )

        if not source_path.exists():
            source_path.write_text(manifest_url + "\n", encoding="utf-8")

        print("Loading existing manifest")
        return manifest

    if source_path.exists():
        cached_url = source_path.read_text(encoding="utf-8").strip()
        if cached_url != manifest_url:
            raise ValueError(
                f"Source directory belongs to a different manifest: {cached_url!r}. "
                "Choose a new --source-id."
            )

    response = requests.get(manifest_url, timeout=30)
    response.raise_for_status()
    manifest = response.json()

    if not isinstance(manifest, dict):
        raise ValueError("The IIIF manifest response must be a JSON object")

    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    source_path.write_text(manifest_url + "\n", encoding="utf-8")

    print("Manifest downloaded:", manifest_path)
    return manifest


def inspect_canvases(manifest: dict, show_details: bool = True) -> list[dict]:
    images = []

    for canvas in manifest.get("items", []):
        if show_details:
            print("\nCanvas ID:", canvas.get("id"))
            print("Width:", canvas.get("width"))
            print("Height:", canvas.get("height"))

        for page in canvas.get("items", []):
            for annotation in page.get("items", []):
                if annotation.get("motivation") != "painting":
                    continue
            
                body = annotation.get("body", {})
            
                if not isinstance(body, dict) or body.get("type") != "Image":
                    continue

                image_url = body.get("id")
                if not image_url:
                    continue

                services = body.get("service") or []
                if isinstance(services, dict):
                    services = [services]

                service_url = None
                for service in services:
                    if isinstance(service, dict) and service.get("id"):
                        service_url = service["id"]
                        break

                images.append({
                    "canvas_id": canvas.get("id"),
                    "canvas_label": canvas.get("label"),
                    "canvas_rights": canvas.get("rights"),
                    "canvas_width": canvas.get("width"),
                    "canvas_height": canvas.get("height"),
                    "canvas_metadata": canvas.get("metadata", []),
                    "canvas_see_also": canvas.get("seeAlso", []),
                    "image_url": image_url,
                    "image_format": body.get("format"),
                    "service_url": service_url,
                })

                if show_details:
                    print("Annotation:", annotation.get("motivation"))
                    print("Image URL:", image_url)
                    print("Image format:", body.get("format"))
                    print("Image service:", service_url)

    return images


def download_image(image_url: str, image_path: Path) -> None:
    if image_path.exists():
        print("Image already downloaded:", image_path)
        return

    image_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = image_path.with_suffix(".part")

    try:
        with requests.get(image_url, timeout=60, stream=True) as response:
            response.raise_for_status()
            with temporary_path.open("wb") as file:
                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if chunk:
                        file.write(chunk)
        temporary_path.replace(image_path)
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise

    print("Image saved:", image_path)


def inspect_image_service(service_url: str, data_dir: Path, index: int) -> dict:
    info_path = data_dir / f"image-info-{index:03d}.json"

    if info_path.exists():
        print("Loading existing image service information")
        image_info = json.loads(info_path.read_text(encoding="utf-8"))
        cached_service = image_info.get("id")
        if cached_service and cached_service.rstrip("/") != service_url.rstrip("/"):
            raise ValueError(
                f"Cached image service for image {index} does not match this canvas. "
                "Check whether the manifest has changed."
            )
    else:
        response = requests.get(f"{service_url.rstrip('/')}/info.json", timeout=30)
        response.raise_for_status()
        image_info = response.json()
        info_path.write_text(
            json.dumps(image_info, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )

    print("\nImage service information:")
    print("Width:", image_info.get("width"))
    print("Height:", image_info.get("height"))
    print("Profile:", image_info.get("profile"))
    print("Available sizes:", image_info.get("sizes"))
    print("Service type:", image_info.get("type"))
    print("Maximum width:", image_info.get("maxWidth"))
    print("Maximum height:", image_info.get("maxHeight"))
    print("Maximum area:", image_info.get("maxArea"))

    return image_info


def calculate_sha256(file_path: Path) -> str:
    checksum = hashlib.sha256()
    with file_path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def save_acquisition_metadata(manifest: dict, images: list[dict], source_id: str, manifest_url: str, data_dir: Path) -> None:
    records = []

    for index, image in enumerate(images, start=1):
        preview_path = data_dir / f"canvas-{index:03d}-preview.jpg"
        max_image_path = data_dir / f"canvas-{index:03d}-max.jpg"
        direct_image_path = data_dir / f"canvas-{index:03d}.jpg"
        has_direct_image = direct_image_path.is_file()
        has_preview = preview_path.is_file()
        has_max_image = max_image_path.is_file()

        info_path = data_dir / f"image-info-{index:03d}.json"

        image_info = (
            json.loads(info_path.read_text(encoding="utf-8"))
            if info_path.is_file()
            else {}
        )

        records.append({
            "canvas_id": image["canvas_id"],
            "canvas_label": image.get("canvas_label"),
            "canvas_rights": image.get("canvas_rights"),
            "canvas_width": image.get("canvas_width"),
            "canvas_height": image.get("canvas_height"),
            "canvas_metadata": image.get("canvas_metadata", []),
            "canvas_see_also": image.get("canvas_see_also", []),
            "image_url": image["image_url"],
            "image_format": image.get("image_format"),
            "image_service_rights": image_info.get("rights"),
            "service_url": image["service_url"],
            "direct_image_path": (str(direct_image_path) if has_direct_image else None),
            "direct_image_sha256": (calculate_sha256(direct_image_path) if has_direct_image else None),
            "preview_path": str(preview_path) if has_preview else None,
            "preview_sha256": calculate_sha256(preview_path) if has_preview else None,
            "max_image_path": str(max_image_path) if has_max_image else None,
            "max_image_sha256": calculate_sha256(max_image_path) if has_max_image else None,
            "image_type": "unknown",
        })

    metadata = {
        "source_id": source_id,
        "metadata_recorded_at": datetime.now(timezone.utc).isoformat(),
        "manifest_url": manifest_url,
        "manifest_id": manifest.get("id"),
        "manifest_metadata": manifest.get("metadata", []),
        "homepage": manifest.get("homepage"),
        "see_also": manifest.get("seeAlso"),
        "nav_date": manifest.get("navDate"),
        "label": manifest.get("label"),
        "rights": manifest.get("rights"),
        "provider": manifest.get("provider"),
        "summary": manifest.get("summary"),
        "required_statement": manifest.get("requiredStatement"),
        "images": records,
    }

    metadata_path = data_dir / "acquisition-metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8",)
    print("Acquisition metadata saved:", metadata_path)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Retrieve images and metadata from an IIIF manifest"
    )
    parser.add_argument("--manifest-url", required=True)
    parser.add_argument("--source-id", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", args.source_id):
        parser.error("--source-id must contain lowercase letters, numbers or hyphens")

    data_dir = DATA_ROOT / args.source_id
    data_dir.mkdir(parents=True, exist_ok=True)
    manifest = load_manifest(args.manifest_url, data_dir)

    print("Manifest type:", manifest.get("type"))
    print("Identifier:", manifest.get("id"))
    print("Label:", manifest.get("label"))
    print("Rights:", manifest.get("rights"))

    images = inspect_canvases(manifest, show_details=SHOW_CANVASES)
    print("Images discovered:", len(images))

    if images and (CHECK_IMAGE_SERVICE or DOWNLOAD_PREVIEWS or DOWNLOAD_MAX_IMAGE):
        for index, image in enumerate(images, start=1):
            print(f"\nProcessing image {index}/{len(images)}")
            print("Canvas:", image["canvas_id"])

            service_url = image["service_url"]
            if not service_url:
                print(f"Canvas {index}: no image service available")
                continue

            image_info = inspect_image_service(service_url, data_dir, index)
            if image_info.get("type") != "ImageService3":
                print(f"Canvas {index}: unsupported image service version")
                continue

            if DOWNLOAD_PREVIEWS:
                preview_url = f"{service_url.rstrip('/')}/full/800,/0/default.jpg"
                preview_path = data_dir / f"canvas-{index:03d}-preview.jpg"
                download_image(preview_url, preview_path)

            if DOWNLOAD_MAX_IMAGE:
                max_url = f"{service_url.rstrip('/')}/full/max/0/default.jpg"
                max_image_path = data_dir / f"canvas-{index:03d}-max.jpg"
                download_image(max_url, max_image_path)

    if DOWNLOAD_IMAGES:
        for index, image in enumerate(images, start=1):
            image_path = data_dir / f"canvas-{index:03d}.jpg"
            download_image(image["image_url"], image_path)

    save_acquisition_metadata(manifest, images, args.source_id, args.manifest_url, data_dir)


if __name__ == "__main__":
    main()
