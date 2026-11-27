import hashlib
import json
import sys
import tempfile
import unittest
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import acquire_commons_writing as subject


def jpeg_bytes():
    output = BytesIO()
    Image.new("RGB", (4, 4), (20, 40, 60)).save(output, format="JPEG")
    return output.getvalue() + b"\x00" * 9103 + b"SEFT"


def info(pageid, data):
    return {"pageid": pageid, "title": f"File:{pageid}.jpg",
            "downloadUrl": f"https://upload.wikimedia.org/wikipedia/commons/{pageid}.jpg",
            "pageUrl": f"https://commons.wikimedia.org/wiki/File:{pageid}.jpg",
            "license": "CC-BY-SA-4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "commonsSha1": hashlib.sha1(data).hexdigest(), "bytesReported": len(data), "sourceMetadata": {"Artist": {"value": "Photographer"}},
            "trainingDataApproved": False}


def options(allow_failures=True):
    return SimpleNamespace(script="latin", medium="inscription", max_image_mb=25, user_agent="agent", timeout=10,
                           continue_on_item_error=allow_failures)


class CommonsWritingResumabilityTests(unittest.TestCase):
    def make_plan(self, root, candidates):
        plan = {"schemaVersion": 1, "query": "Latin inscription", "scriptQuery": "latin",
                "mediumQuery": "inscription", "trainingDataApproved": False,
                "candidates": [{"status": "PREVIEW_READY", **candidate} for candidate in candidates]}
        (root / "commons-writing-plan.json").write_text(json.dumps(plan))

    def test_item_failure_audited_without_aborting_overnight_mode(self):
        image = jpeg_bytes()
        first, second = info(42, image), info(43, image)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_plan(root, [first, second])
            def acquire(current, destination, *args):
                if current["pageid"] == 43:
                    raise ValueError("broken individual image")
                destination.write_bytes(image)
                return "DOWNLOADED", {"sha256": hashlib.sha256(image).hexdigest(), "fileSize": len(image), **current}
            with patch.object(subject, "load_query", return_value="Latin inscription"), \
                 patch.object(subject, "imageinfo", side_effect=[first, second]), \
                 patch.object(subject.commons, "acquire_file", side_effect=acquire):
                self.assertEqual(0, subject.download(options(), root))
            audit = [json.loads(line) for line in (root / "commons-writing-audit.jsonl").read_text().splitlines()]
            self.assertEqual(["DOWNLOADED", "FAILED"], [entry["status"] for entry in audit])
            self.assertFalse(any(entry["trainingDataApproved"] for entry in audit))
            self.assertEqual(image, (root / "images/42.jpg").read_bytes())
            sidecar = json.loads((root / "images/42.json").read_text())
            self.assertEqual("FULL_JPEG_DECODE_AND_UPSTREAM_SHA1", sidecar["imageValidation"])
            self.assertFalse(sidecar["trainingDataApproved"])

    def test_item_failure_still_nonzero_without_opt_in(self):
        image = jpeg_bytes()
        candidate = info(43, image)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_plan(root, [candidate])
            with patch.object(subject, "load_query", return_value="Latin inscription"), \
                 patch.object(subject, "imageinfo", return_value=candidate), \
                 patch.object(subject.commons, "acquire_file", side_effect=ValueError("corrupted")):
                self.assertEqual(1, subject.download(options(False), root))

    def test_network_cooldown_still_halts_even_in_overnight_mode(self):
        image = jpeg_bytes()
        candidate = info(42, image)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_plan(root, [candidate])
            error = subject.commons.AcquisitionNetworkError("image-download", "upload.wikimedia.org", ConnectionError("offline"))
            with patch.object(subject, "load_query", return_value="Latin inscription"), \
                 patch.object(subject, "imageinfo", return_value=candidate), \
                 patch.object(subject.commons, "acquire_file", side_effect=error):
                self.assertEqual(1, subject.download(options(), root))
            audit = [json.loads(line) for line in (root / "commons-writing-audit.jsonl").read_text().splitlines()]
            self.assertEqual("PAUSED", audit[-1]["status"])

    def test_no_eligible_images_is_not_a_batch_failure_with_opt_in(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_plan(root, [])
            with patch.object(subject, "load_query", return_value="Latin inscription"):
                self.assertEqual(0, subject.download(options(), root))
                self.assertEqual(1, subject.download(options(False), root))


if __name__ == "__main__":
    unittest.main()
