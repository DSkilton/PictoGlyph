import argparse
import re
import json
from pathlib import Path

import requests


DATA_ROOT = Path(
    "/mnt/i/PictoGlyph/data/research-datasets/iiif"
)

SHOW_CANVASES = False
CHECK_IMAGE_SERVICE = True
DOWNLOAD_IMAGES = False
DOWNLOAD_MAX_IMAGE = True

def load_manifest(manifest_url: str, data_dir: Path):
    manifest_path = data_dir / "manifest.json"

    if manifest_path.exists():
        print("Loading existing manifest")
        return json.loads(
            manifest_path.read_text(encoding="utf-8")
        )

    response = requests.get(manifest_url, timeout=30)
    response.raise_for_status()

    manifest = response.json()

    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8"
    )

    print("Manifest downloaded:", manifest_path)

    return manifest


def inspect_canvases(manifest, show_details=True):
    images = []

    for canvas in manifest.get("items", []):
        if show_details:
            print("\nCanvas ID:", canvas.get("id"))
            print("Width:", canvas.get("width"))
            print("Height:", canvas.get("height"))

        annotation_pages = canvas.get("items", [])

        for page in annotation_pages:
            annotations = page.get("items", [])

            for annotation in annotations:
                body = annotation.get("body", {})

                if not isinstance(body, dict):
                    continue

                image_url = body.get("id")

                if not image_url:
                    continue

                services = body.get("service", [])

                if isinstance(services, dict):
                    services = [services]

                service_url = None

                for service in services:
                    service_url = service.get("id")

                    if service_url:
                        break

                images.append({
                    "canvas_id": canvas.get("id"),
                    "image_url": image_url,
                    "image_format": body.get("format"),
                    "service_url": service_url
                })

                if show_details:
                    print("Annotation:", annotation.get("motivation"))
                    print("Image URL:", image_url)
                    print("Image format:", body.get("format"))
                    print("Image service:", service_url)

    return images


def download_image(image_url, image_path):
    if image_path.exists():
        print("Image already downloaded:", image_path)
        return

    image_path.parent.mkdir(parents=True, exist_ok=True)

    temporary_path = image_path.with_suffix(".part")

    try:
        with requests.get(
            image_url,
            timeout=60,
            stream=True
        ) as response:
            response.raise_for_status()

            with temporary_path.open("wb") as file:
                for chunk in response.iter_content(
                    chunk_size=1024 * 1024
                ):
                    if chunk:
                        file.write(chunk)

        temporary_path.replace(image_path)

    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise

    print("Image saved:", image_path)


def inspect_image_service(service_url: str, data_dir: Path):
    info_path = data_dir / "image-info.json"

    if info_path.exists():
        print("Loading existing image service information")
        image_info = json.loads(
            info_path.read_text(encoding="utf-8")
        )
    else:
        response = requests.get(
            f"{service_url.rstrip('/')}/info.json",
            timeout=30
        )
        response.raise_for_status()

        image_info = response.json()

        info_path.write_text(
            json.dumps(image_info, indent=2, ensure_ascii=False),
            encoding="utf-8"
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


def main():
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

    if CHECK_IMAGE_SERVICE and images:
        service_url = images[0]["service_url"]

        if service_url:
            image_info = inspect_image_service(service_url, data_dir)

            if image_info.get("type") == "ImageService3":
                max_url = f"{service_url.rstrip('/')}/full/max/0/default.jpg"
                print("Maximum resolution URL:", max_url)

                if DOWNLOAD_MAX_IMAGE:
                    max_image_path = data_dir / "canvas-001-max.jpg"
            
                    download_image(max_url, max_image_path)

    if DOWNLOAD_IMAGES:
        for index, image in enumerate(images, start=1):
            image_path = data_dir / f"canvas-{index:03d}.jpg"

            download_image(
                image["image_url"],
                image_path
            )


if __name__ == "__main__":
    main()
