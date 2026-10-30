import json
import requests
from pathlib import Path

DATA_DIR = Path(
    "/mnt/i/PictoGlyph/data/research-datasets/iiif/test-rfwqo"
)

DATA_DIR.mkdir(parents=True, exist_ok=True)

manifest_url = "https://iiif.micr.io/RFwqO/manifest"

response = requests.get(manifest_url, timeout=30)
response.raise_for_status()

manifest = response.json()

manifest_path = DATA_DIR / "manifest.json"

manifest_path.write_text(
    json.dumps(manifest, indent=2, ensure_ascii=False),
    encoding="utf-8"
)

print("Manifest saved:", manifest_path)

print("Manifest type:", manifest.get("type"))
print("Identifier:", manifest.get("id")) 
print("Label:", manifest.get("label"))
print("Rights:", manifest.get("rights"))
print("Canvases:", len(manifest.get("items", [])))

for canvas in manifest.get("items", []):
    print("\nCanvas ID:", canvas.get("id"))
    print("Width:", canvas.get("width"))
    print("Height:", canvas.get("height"))

    annotation_pages = canvas.get("items", [])

    print("Annotation pages:", len(annotation_pages))

    for page in annotation_pages:
        annotations = page.get("items", [])
    
        for annotation in annotations:
            body = annotation.get("body", {})
    
            print("Annotation type:", annotation.get("motivation"))
            print("Image URL:", body.get("id"))

            image_url = body.get("id")

            if image_url:
                image_path = Path(
                    "/mnt/i/PictoGlyph/data/research-datasets/"
                    "iiif/test-rfwqo/canvas-001.jpg"
                )
            
                if not image_path.exists():
                    image_response = requests.get(image_url, timeout=60)
                    image_response.raise_for_status()
            
                    image_path.write_bytes(image_response.content)
            
                    print("Image saved:", image_path)
                else:
                    print("Image already downloaded:", image_path)
            print("Image format:", body.get("format"))
    
            for service in body.get("service", []):
                print("Image service:", service.get("id"))

service_url = "https://iiif.micr.io/RFwqO"

response = requests.get(f"{service_url}/info.json", timeout=30)
response.raise_for_status()

image_info = response.json()

print("\nImage service information:")
print("Width:", image_info.get("width"))
print("Height:", image_info.get("height"))
print("Profile:", image_info.get("profile"))
print("Available sizes:", image_info.get("sizes"))