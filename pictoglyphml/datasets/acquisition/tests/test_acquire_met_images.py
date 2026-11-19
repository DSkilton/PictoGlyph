"""Tests for local Met JPEG acquisition and immutable per-image evidence."""

import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from acquire_met_images import (acquire_image, build_parser, fetch_jpeg, image_paths, run,
                                validate_image_url, verify_existing)

JPEG_BYTES = b"\xff\xd8\xff\xe0Met test bytes\xff\xd9"
SOURCE_ID = "met-openaccess-metadata"
IMAGE_URL = "https://images.metmuseum.org/CRDImages/eg/original/pilot.jpg"
SECOND_IMAGE_URL = "https://images.metmuseum.org/CRDImages/eg/original/second.jpg"


class ImageResponse(io.BytesIO):
    def __init__(self, body=JPEG_BYTES, content_type="image/jpeg", content_length=None):
        super().__init__(body)
        self.headers = {"Content-Type": content_type}
        if content_length is not None:
            self.headers["Content-Length"] = str(content_length)


class MetImageAcquisitionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.shortlist = self.root / "pilot.jsonl"
        self.shortlist.write_text('{"objectId":550876}\n{"objectId":550879}\n', encoding="utf-8")
        self.output = self.root / "acquired"
        self.environment = patch.dict(os.environ, {"PICTOGLYPH_RESEARCH_IMPORT_TOKEN": "local-test-token"})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.temporary.cleanup()

    def args(self, execute=False):
        parameters = ["--input", str(self.shortlist), "--output-dir", str(self.output),
                      "--batch-size", "2", "--max-objects", "2"]
        if execute:
            parameters.append("--execute")
        return build_parser().parse_args(parameters)

    def preview(self, first_url=IMAGE_URL, second_url=SECOND_IMAGE_URL, second_public=True):
        candidates = []
        for object_id, url, is_public in ((550876, first_url, True), (550879, second_url, second_public)):
            candidates.append({"sourceRecordId": str(object_id), "title": "Inlay, hieroglyph",
                               "imageUrls": [url] if is_public else [],
                               "rights": "PUBLIC_DOMAIN_REPORTED_BY_SOURCE" if is_public else "REVIEW_IMAGE_RIGHTS",
                               "rawRecord": {"objectID": object_id, "isPublicDomain": is_public,
                                             "accessionNumber": str(object_id), "metadataDate": "2026-07-31"}})
        return {"sourceId": SOURCE_ID, "rawResponse": {"requestedObjectIds": [550876, 550879],
                "retrievalMethod": "EXACT_OBJECT_ID"}, "candidates": candidates}

    def test_url_restricts_scheme_host_and_parameters(self):
        self.assertEqual(IMAGE_URL, validate_image_url(IMAGE_URL))
        bad_urls = ["http://images.metmuseum.org/a.jpg", "https://example.org/a.jpg",
                    "https://images.metmuseum.org.evil.org/a.jpg",
                    "https://user:password@images.metmuseum.org/a.jpg",
                    "https://images.metmuseum.org:444/a.jpg",
                    "https://images.metmuseum.org/a.jpg#fragment",
                    "https://images.metmuseum.org/a.jpg?override=1"]
        for url in bad_urls:
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate_image_url(url)

    def test_dry_run_never_downloads_images_or_creates_sidecars(self):
        with patch("acquire_met_images.post_json", return_value=self.preview()) as api:
            with patch("acquire_met_images.fetch_jpeg") as download:
                report = run(self.args())
        self.assertEqual("COMPLETE", report["status"])
        self.assertEqual("PREVIEW_ONLY", report["mode"])
        self.assertEqual(2, report["imagesSelected"])
        self.assertEqual(0, report["imagesDownloaded"])
        self.assertFalse((self.output / "images").exists())
        download.assert_not_called()
        api.assert_called_once()

    def test_execute_downloads_and_then_reuses_identical_images(self):
        with patch("acquire_met_images.post_json", return_value=self.preview()):
            with patch("acquire_met_images.build_opener") as opener:
                opener.return_value.open.side_effect = [ImageResponse(), ImageResponse()]
                first = run(self.args(execute=True))
                self.assertEqual(2, first["imagesDownloaded"])
                self.assertEqual(0, first["imagesReused"])
                self.assertEqual(2, opener.return_value.open.call_count)
                self.assertNotIn("X-PictoGlyph-Import-Token", opener.return_value.open.call_args_list[0].args[0].headers)
            with patch("acquire_met_images.build_opener") as opener:
                second = run(self.args(execute=True))
                opener.assert_not_called()
        self.assertEqual(0, second["imagesDownloaded"])
        self.assertEqual(2, second["imagesReused"])
        self.assertFalse(second["databaseUpdated"])
        image_path, metadata_path = image_paths(self.output, 550876, IMAGE_URL)
        self.assertEqual(JPEG_BYTES, image_path.read_bytes())
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        self.assertFalse(metadata["trainingDataApproved"])
        self.assertFalse(metadata["linkedToPostgreSql"])
        self.assertEqual("JPEG_SIGNATURE_ONLY", metadata["verificationStatus"])
        events = [json.loads(line)["event"] for line in (self.output / "met-image-acquisition-audit.jsonl").read_text().splitlines()]
        self.assertIn("met_image_downloaded", events)
        self.assertIn("met_image_reused", events)

    def test_partial_failure_resumes_without_redownloading_completed_image(self):
        with patch("acquire_met_images.post_json", return_value=self.preview()):
            with patch("acquire_met_images.build_opener") as opener:
                opener.return_value.open.side_effect = [ImageResponse(), ImageResponse(content_type="text/html")]
                with self.assertRaisesRegex(ValueError, "content type"):
                    run(self.args(execute=True))
            first_path, _ = image_paths(self.output, 550876, IMAGE_URL)
            self.assertTrue(first_path.exists())
            with patch("acquire_met_images.build_opener") as opener:
                opener.return_value.open.return_value = ImageResponse()
                report = run(self.args(execute=True))
                self.assertEqual(1, opener.return_value.open.call_count)
        self.assertEqual("COMPLETE", report["status"])
        self.assertEqual(1, report["imagesReused"])
        self.assertEqual(1, report["imagesDownloaded"])

    def test_rejects_symlinked_acquisition_directory(self):
        real_directory = self.root / "external"
        real_directory.mkdir()
        link = self.root / "linked"
        link.symlink_to(real_directory, target_is_directory=True)
        args = self.args()
        args.output_dir = link
        with self.assertRaisesRegex(ValueError, "must not be a symlink"):
            run(args)

    def test_rejects_altered_existing_file_without_overwriting(self):
        with patch("acquire_met_images.build_opener") as opener:
            opener.return_value.open.return_value = ImageResponse()
            acquire_image(self.output, 550876, IMAGE_URL, {}, 10, 1024)
        image_path, metadata_path = image_paths(self.output, 550876, IMAGE_URL)
        image_path.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "checksum or size"):
            verify_existing(image_path, metadata_path, 550876, IMAGE_URL)
        with patch("acquire_met_images.build_opener") as opener:
            with self.assertRaisesRegex(ValueError, "checksum or size"):
                acquire_image(self.output, 550876, IMAGE_URL, {}, 10, 1024)
            opener.assert_not_called()

    def test_missing_sidecar_is_not_silently_repaired(self):
        image_path, metadata_path = image_paths(self.output, 550876, IMAGE_URL)
        image_path.parent.mkdir(parents=True)
        image_path.write_bytes(JPEG_BYTES)
        with self.assertRaisesRegex(ValueError, "Incomplete image/metadata"):
            verify_existing(image_path, metadata_path, 550876, IMAGE_URL)

    def test_rejects_non_jpeg_content(self):
        destination = self.output / "test.jpg"
        with patch("acquire_met_images.build_opener") as opener:
            opener.return_value.open.return_value = ImageResponse(body=b"not a jpeg")
            with self.assertRaisesRegex(ValueError, "not a complete JPEG"):
                fetch_jpeg(IMAGE_URL, destination, 1024, 5)
        self.assertFalse(destination.exists())
        self.assertEqual([], list(self.output.glob("**/*.part")))

    def test_rejects_unexpected_mime_type(self):
        destination = self.output / "test.jpg"
        with patch("acquire_met_images.build_opener") as opener:
            opener.return_value.open.return_value = ImageResponse(content_type="text/html")
            with self.assertRaisesRegex(ValueError, "content type"):
                fetch_jpeg(IMAGE_URL, destination, 1024, 5)
        self.assertFalse(destination.exists())

    def test_rejects_oversized_stream_and_removes_partial(self):
        destination = self.output / "test.jpg"
        with patch("acquire_met_images.build_opener") as opener:
            opener.return_value.open.return_value = ImageResponse(body=JPEG_BYTES * 20)
            with self.assertRaisesRegex(ValueError, "size limit"):
                fetch_jpeg(IMAGE_URL, destination, 32, 5)
        self.assertFalse(destination.exists())
        self.assertEqual([], list(self.output.glob("**/*.part")))

    def test_rejects_upstream_preview_identity_mismatch(self):
        response = self.preview()
        response["candidates"][0]["sourceRecordId"] = "999999"
        with patch("acquire_met_images.post_json", return_value=response):
            with patch("acquire_met_images.build_opener") as opener:
                with self.assertRaisesRegex(ValueError, "unexpected record"):
                    run(self.args(execute=True))
                opener.assert_not_called()
        report = json.loads((self.output / "met-image-acquisition-last-run.json").read_text())
        self.assertEqual("FAILED", report["status"])

    def test_restricted_object_is_not_downloaded(self):
        with patch("acquire_met_images.post_json", return_value=self.preview(second_public=False)):
            with patch("acquire_met_images.build_opener") as opener:
                opener.return_value.open.return_value = ImageResponse()
                report = run(self.args(execute=True))
        self.assertEqual(1, report["skippedRights"])
        self.assertEqual(1, report["imagesDownloaded"])
        self.assertEqual(1, opener.return_value.open.call_count)

    def test_bad_shortlist_rejected_before_request(self):
        self.shortlist.write_text('{"objectId":550876}\n{"objectId":550876}\n', encoding="utf-8")
        with patch("acquire_met_images.post_json") as api:
            with self.assertRaisesRegex(ValueError, "Duplicate objectId"):
                run(self.args())
            api.assert_not_called()


if __name__ == "__main__":
    unittest.main()
