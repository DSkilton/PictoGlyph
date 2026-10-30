import requests
from pathlib import Path

manifest_url = "https://iiif.micr.io/RFwqO/manifest"

response = requests.get(manifest_url, timeout=30)
response.raise_for_status()

manifest = response.json()

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
            print("Image format:", body.get("format"))
    
            for service in body.get("service", []):
                print("Image service:", service.get("id"))