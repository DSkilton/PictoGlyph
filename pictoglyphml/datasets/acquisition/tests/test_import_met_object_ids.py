import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_met_object_ids import (assess_preview, build_parser, load_candidates, load_checkpoint,
                                   run, validate_api_base, verify_import)

SOURCE_ID = "met-openaccess-metadata"


def make_preview(object_ids, restricted=(), without_images=()):
    candidates = []
    for object_id in object_ids:
        public_domain = object_id not in restricted
        images = [] if object_id in without_images or not public_domain else ["https://images.metmuseum.org/test.jpg"]
        candidates.append({"sourceRecordId": str(object_id), "title": "Inlay, hieroglyph",
                           "rights": "PUBLIC_DOMAIN_REPORTED_BY_SOURCE" if public_domain else "REVIEW_IMAGE_RIGHTS",
                           "imageUrls": images, "rawRecord": {"objectID": object_id, "isPublicDomain": public_domain},
                           "explicitSignEvidence": False})
    return {"sourceId": SOURCE_ID, "candidates": candidates,
            "rawResponse": {"requestedObjectIds": object_ids, "retrievalMethod": "EXACT_OBJECT_ID"}}


def make_import(object_ids, created=0):
    return {"sourceId": SOURCE_ID, "candidatesFound": len(object_ids), "recordsCreated": created,
            "recordsReused": len(object_ids) - created, "imageReferencesCreated": 0, "skippedWithoutIdentity": 0}


class ImportMetObjectIdsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "curated-met.jsonl"
        self.source.write_text("".join(json.dumps({"sourceId": SOURCE_ID, "objectId": value,
                                                 "publicDomainReportedByCsv": True}) + "\n"
                                       for value in (550876, 550879)), encoding="utf-8")
        self.output = self.root / "run"
        self.token = patch.dict(os.environ, {"PICTOGLYPH_RESEARCH_IMPORT_TOKEN": "test-only-token"})
        self.token.start()
        self.addCleanup(self.token.stop)

    def args(self, *options):
        return build_parser().parse_args(["--input", str(self.source), "--output-dir", str(self.output),
                                          "--pause-seconds", "0", *options])

    def test_catalogue_jsonl_accepts_only_distinct_eligible_met_ids(self):
        self.source.write_text("".join(json.dumps(record) + "\n" for record in (
            {"objectId": 550876, "publicDomainReportedByCsv": True},
            {"objectId": 550879, "publicDomainReportedByCsv": False},
            {"objectId": 550880})), encoding="utf-8")
        ids, excluded = load_candidates(self.source)
        self.assertEqual([550876, 550880], ids)
        self.assertEqual(1, excluded)
        self.source.write_text('{"objectId":550876}\n{"objectId":550876}\n', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            load_candidates(self.source)

    def test_preview_verifies_ids_and_restricts_rights_and_missing_images(self):
        preview = make_preview([550876, 550879, 550880], restricted=(550879,), without_images=(550880,))
        outcomes = assess_preview(preview, [550876, 550879, 550880])
        self.assertEqual("READY_FOR_IMPORT", outcomes[550876]["status"])
        self.assertEqual("SKIPPED_RIGHTS", outcomes[550879]["status"])
        self.assertEqual("SKIPPED_NO_IMAGE", outcomes[550880]["status"])
        with self.assertRaisesRegex(ValueError, "requestedObjectIds"):
            assess_preview(preview, [550876, 550880, 550879])
        preview["candidates"][0]["rawRecord"]["objectID"] = 55
        with self.assertRaisesRegex(ValueError, "raw museum record"):
            assess_preview(preview, [550876, 550879, 550880])

    def test_preview_mode_does_not_call_import_or_create_a_checkpoint(self):
        with patch("import_met_object_ids.post_json", return_value=make_preview([550876, 550879])) as api:
            result = run(self.args())
        self.assertEqual("COMPLETE", result["status"])
        self.assertEqual("PREVIEW_ONLY", result["mode"])
        self.assertEqual(2, result["readyThisRun"])
        self.assertEqual(0, result["importedThisRun"])
        self.assertEqual("preview-ids", api.call_args.args[1])
        self.assertEqual(1, api.call_count)
        self.assertFalse((self.output / "met-exact-id-checkpoint.json").exists())
        self.assertTrue((self.output / "met-exact-id-report.json").exists())

    def test_execute_imports_only_verified_objects_and_checkpoints_skips(self):
        with patch("import_met_object_ids.post_json", side_effect=[
                make_preview([550876, 550879], restricted=(550879,)), make_import([550876], created=1)]) as api:
            result = run(self.args("--execute"))
        self.assertEqual(1, result["importedThisRun"])
        self.assertEqual(1, result["skippedRightsThisRun"])
        self.assertEqual([550876], api.call_args.args[2])
        checkpoint = json.loads((self.output / "met-exact-id-checkpoint.json").read_text())
        self.assertEqual("IMPORTED", checkpoint["processed"]["550876"])
        self.assertEqual("SKIPPED_RIGHTS", checkpoint["processed"]["550879"])
        with patch("import_met_object_ids.post_json") as api:
            resumed = run(self.args("--execute"))
        api.assert_not_called()
        self.assertEqual(2, resumed["previouslyProcessed"])

    def test_failed_batch_is_retried_without_repeating_completed_batch(self):
        with patch("import_met_object_ids.post_json", side_effect=[
                make_preview([550876]), make_import([550876], created=1), RuntimeError("upstream unavailable")]):
            with self.assertRaisesRegex(RuntimeError, "upstream unavailable"):
                run(self.args("--execute", "--batch-size", "1"))
        checkpoint = json.loads((self.output / "met-exact-id-checkpoint.json").read_text())
        self.assertEqual({"550876": "IMPORTED"}, checkpoint["processed"])
        with patch("import_met_object_ids.post_json", side_effect=[make_preview([550879]), make_import([550879])]) as api:
            resumed = run(self.args("--execute", "--batch-size", "1"))
        self.assertEqual(1, resumed["previouslyProcessed"])
        self.assertEqual(1, resumed["importedThisRun"])
        self.assertEqual([550879], api.call_args.args[2])
        self.assertEqual(2, api.call_count)

    def test_import_count_mismatch_is_not_checkpointed(self):
        invalid = make_import([550876, 550879])
        invalid["recordsReused"] = 1
        with patch("import_met_object_ids.post_json", side_effect=[make_preview([550876, 550879]), invalid]):
            with self.assertRaisesRegex(ValueError, "every requested object"):
                run(self.args("--execute"))
        self.assertFalse((self.output / "met-exact-id-checkpoint.json").exists())

    def test_recheck_skipped_revisits_only_previous_skips(self):
        with patch("import_met_object_ids.post_json", side_effect=[
                make_preview([550876, 550879], restricted=(550879,)), make_import([550876])]):
            run(self.args("--execute"))
        with patch("import_met_object_ids.post_json", side_effect=[make_preview([550879]), make_import([550879])]) as api:
            result = run(self.args("--execute", "--recheck-skipped"))
        self.assertEqual(1, result["previouslyProcessed"])
        self.assertEqual(1, result["importedThisRun"])
        self.assertEqual([550879], api.call_args.args[2])

    def test_loopback_http_posts_exact_ids_with_token(self):
        received = []

        class LocalApiHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                object_ids = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                received.append((self.path, object_ids, self.headers.get("X-PictoGlyph-Import-Token")))
                response = make_import(object_ids) if self.path.endswith("/import-ids") else make_preview(object_ids)
                payload = json.dumps(response).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, format, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), LocalApiHandler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            report = run(self.args("--execute", "--api-base", f"http://127.0.0.1:{server.server_port}"))
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=2)
        self.assertEqual(2, report["importedThisRun"])
        self.assertEqual(["preview-ids", "import-ids"], [url.split("/")[-1] for url, _, _ in received])
        self.assertTrue(all(ids == [550876, 550879] and token == "test-only-token" for _, ids, token in received))

    def test_resume_rejects_changed_shortlist(self):
        with patch("import_met_object_ids.post_json", side_effect=[make_preview([550876, 550879]), make_import([550876, 550879])]):
            run(self.args("--execute"))
        self.source.write_text(self.source.read_text() + '{"objectId":550881}\n', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "different shortlist"):
            run(self.args("--execute"))

    def test_local_api_restriction_and_result_validation(self):
        with self.assertRaisesRegex(ValueError, "Only a local"):
            validate_api_base("https://example.com")
        with self.assertRaisesRegex(ValueError, "Only a local"):
            validate_api_base("http://127.0.0.1:8080/other")
        with self.assertRaisesRegex(ValueError, "unexpected source"):
            verify_import({"sourceId": "other", "candidatesFound": 2}, [550876, 550879])

    def test_missing_token_stops_before_network(self):
        with patch.dict(os.environ, {"PICTOGLYPH_RESEARCH_IMPORT_TOKEN": ""}):
            with patch("import_met_object_ids.post_json") as api:
                with self.assertRaisesRegex(ValueError, "Set PICTOGLYPH_RESEARCH_IMPORT_TOKEN"):
                    run(self.args())
            api.assert_not_called()


if __name__ == "__main__":
    unittest.main()
