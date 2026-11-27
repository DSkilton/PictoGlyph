import json
import sys
import tempfile
from io import BytesIO
from PIL import Image
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

test_directory = Path(__file__).resolve().parent
acquisition_directory = test_directory.parent if test_directory.name == "tests" else test_directory
sys.path.insert(0, str(acquisition_directory))
import acquire_met_commons_bulk as subject


def jpeg_image(suffix=b""):
    output = BytesIO()
    Image.new("RGB", (4, 4), (20, 40, 60)).save(output, format="JPEG")
    return output.getvalue() + suffix


class CommonsAcquisitionTests(unittest.TestCase):
    def test_search_uses_exact_met_structured_identifier(self):
        payload = {"query": {"search": [{"pageid": 123, "ns": 6, "title": "File:MET.jpg"},
                                        {"pageid": 99, "ns": 0, "title": "NotFile"}]}}
        with patch.object(subject, "commons_query", return_value=payload) as query:
            result = subject.search_met_files(550876, "agent", 30)
        self.assertEqual([{"pageid": 123, "title": "File:MET.jpg"}], result)
        self.assertEqual(query.call_args.args[0]["srsearch"], "haswbstatement:P3634=550876")

    def test_structured_met_id_is_verified_independently(self):
        good = {"entities": {"M123": {"statements": {"P3634": [{"mainsnak": {"datavalue": {"value": "550876"}}}]}}}}
        with patch.object(subject, "request_bytes", return_value=json.dumps(good).encode()):
            self.assertTrue(subject.verify_structured_met_id(123, 550876, "agent", 30))
            with self.assertRaisesRegex(ValueError, "unexpected values"):
                subject.verify_structured_met_id(123, 550878, "agent", 30)

    def test_commons_imageinfo_rejects_unverified_licence(self):
        def data(name):
            return {"query": {"pages": [{"imageinfo": [{"mime": "image/jpeg", "url": "https://upload.wikimedia.org/a.jpg",
                       "descriptionurl": "https://commons.wikimedia.org/wiki/File:example.jpg",
                       "extmetadata": {"LicenseShortName": {"value": name}}}]}]}}
        with patch.object(subject, "commons_query", return_value=data("CC BY-SA 4.0")):
            with self.assertRaisesRegex(ValueError, "licence not verified"):
                subject.commons_imageinfo("File:example.jpg", "agent", 30)
        with patch.object(subject, "commons_query", return_value=data("CC0 1.0")):
            self.assertEqual("CC0 1.0", subject.commons_imageinfo("File:example.jpg", "agent", 30)["license"])

    def test_jpeg_signature_and_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.jpg"
            data = jpeg_image()
            path.write_bytes(data)
            result = subject.sha256_file_verified(path)
            self.assertEqual(result["fileSize"], len(data))
            path.write_bytes(b"<html>denied</html>")
            with self.assertRaisesRegex(ValueError, "JPEG"):
                subject.sha256_file_verified(path)

    def test_preview_does_not_download_or_attach(self):
        args = SimpleNamespace(execute=False, attach=False, max_images_per_object=20, timeout=20, user_agent="agent", pause_seconds=0)
        match = {"pageid": 123, "title": "File:MET.jpg"}
        image_info = {"downloadUrl": "https://upload.wikimedia.org/a.jpg", "pageUrl": "https://commons.wikimedia.org/wiki/File:MET.jpg",
                      "license": "CC0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/"}
        with patch.object(subject, "search_met_files", return_value=[match]), \
             patch.object(subject, "verify_structured_met_id"), \
             patch.object(subject, "commons_imageinfo", return_value=image_info), \
             patch.object(subject, "acquire_file") as download, \
             patch.object(subject, "local_post") as attach:
            result = subject.process_object(123456, args, Path("/tmp"), "")
        self.assertEqual("PREVIEW_READY", result["status"])
        download.assert_not_called()
        attach.assert_not_called()

    def test_pilot_objects_are_not_attached_twice(self):
        args = SimpleNamespace(attach=True)
        with patch.object(subject, "search_met_files") as search:
            result = subject.process_object(550876, args, Path("/tmp"), "")
        self.assertEqual("LEGACY_PILOT_ALREADY_ATTACHED", result["status"])
        search.assert_not_called()




class CommonsNetworkRecoveryTests(unittest.TestCase):
    def http_error(self, code, retry_after=None):
        from urllib.error import HTTPError
        headers = {} if retry_after is None else {"Retry-After": retry_after}
        return HTTPError("https://upload.wikimedia.org/test.jpg", code, "Unavailable", headers, None)

    def test_image_download_retries_503_then_succeeds(self):
        from io import BytesIO
        from hashlib import sha256
        from unittest.mock import MagicMock
        image = jpeg_image()
        response = BytesIO(image)
        response.headers = {"Content-Type": "image/jpeg", "Content-Length": str(len(image))}
        response.__enter__ = lambda self: self
        response.__exit__ = lambda self, *args: self.close()
        opener = MagicMock()
        opener.open.side_effect = [self.http_error(503), response]
        info = {"downloadUrl": "https://upload.wikimedia.org/wikipedia/commons/a/example.jpg"}
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, "build_opener", return_value=opener), \
             patch.object(subject, "pace_wikimedia"), patch.object(subject.time, "sleep") as sleep:
            path = Path(directory) / "image.jpg"
            status, metadata = subject.acquire_file(info, path, "PictoGlyphTest", 10, 1024)
            self.assertEqual("DOWNLOADED", status)
            self.assertEqual(sha256(image).hexdigest(), metadata["sha256"])
            self.assertEqual(image, path.read_bytes())
            self.assertEqual(opener.open.call_count, 2)
            sleep.assert_called_once_with(5)

    def test_remote_disconnected_is_retried(self):
        from http.client import RemoteDisconnected
        from io import BytesIO
        from unittest.mock import MagicMock
        response = BytesIO(b'{"query":{}}')
        response.__enter__ = lambda self: self
        response.__exit__ = lambda self, *args: self.close()
        opener = MagicMock()
        opener.open.side_effect = [RemoteDisconnected("connection closed"), response]
        with patch.object(subject, "build_opener", return_value=opener), \
             patch.object(subject, "pace_wikimedia"), patch.object(subject.time, "sleep"):
            data = subject.request_bytes("https://commons.wikimedia.org/w/api.php", "PictoGlyphTest")
        self.assertEqual(b'{"query":{}}', data)
        self.assertEqual(2, opener.open.call_count)

    def test_long_retry_after_pauses_without_ignoring_server(self):
        error = self.http_error(503, "180")
        with patch.object(subject, "pace_wikimedia"), patch.object(subject.time, "sleep") as sleep:
            with self.assertRaises(subject.RemoteCooldown) as context:
                subject.retry_remote(lambda: (_ for _ in ()).throw(error), "https://upload.wikimedia.org/a.jpg", "image-download")
        self.assertGreaterEqual(context.exception.retry_after_seconds, 180)
        sleep.assert_not_called()

    def test_rejects_non_jpeg_without_retrying(self):
        from io import BytesIO
        from unittest.mock import MagicMock
        response = BytesIO(b"<html>not an image</html>")
        response.headers = {"Content-Type": "text/html"}
        response.__enter__ = lambda self: self
        response.__exit__ = lambda self, *args: self.close()
        opener = MagicMock()
        opener.open.return_value = response
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, "build_opener", return_value=opener), \
             patch.object(subject, "pace_wikimedia"), patch.object(subject.time, "sleep"):
            with self.assertRaisesRegex(ValueError, "non-JPEG"):
                subject.acquire_file({"downloadUrl": "https://upload.wikimedia.org/test.jpg"}, Path(directory) / "test.jpg", "agent", 10, 1024)
        self.assertEqual(1, opener.open.call_count)

    def test_pauses_batch_after_repeated_remote_failures(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input.jsonl"
            source.write_text('{"objectId":10020}\n')
            args = SimpleNamespace(input=source, output_dir=root / "output", max_objects=4,
                                   max_images_per_object=20, max_image_mb=25, pause_seconds=2.0,
                                   timeout=30, attach=False, execute=True, retry_unmatched=False,
                                   api_base="http://127.0.0.1:8080")
            error = subject.AcquisitionNetworkError("image-download", "upload.wikimedia.org", self.http_error(503))
            with patch.object(subject, "load_candidates", return_value=([10020, 10021, 10022, 10423], 0)), \
                 patch.object(subject, "process_object", side_effect=error) as process, \
                 patch.object(subject.time, "sleep"):
                status = subject.run(args)
            report = json.loads((args.output_dir / "met-commons-last-run.json").read_text())
            checkpoint = json.loads((args.output_dir / "met-commons-checkpoint.json").read_text())
        self.assertEqual(1, status)
        self.assertTrue(report["pausedForNetwork"])
        self.assertEqual(2, report["attemptedThisRun"])
        self.assertEqual(2, process.call_count)
        self.assertEqual({"10020", "10021"}, set(checkpoint["processed"]))
        self.assertEqual("upload.wikimedia.org", report["results"][0]["remoteHost"])

    def test_search_adds_maxlag(self):
        from urllib.parse import urlsplit, parse_qs
        with patch.object(subject, "request_bytes", return_value=b'{"query":{"search":[]}}') as call:
            subject.search_met_files(10020, "agent", 30)
        query = parse_qs(urlsplit(call.call_args.args[0]).query)
        self.assertEqual(["5"], query["maxlag"])




class CommonsApiOverloadTests(unittest.TestCase):
    def test_search_retries_json_busy_error_and_recovers(self):
        busy = b'{"error":{"code":"cirrussearch-too-busy-error","info":"Search is currently too busy"}}'
        success = b'{"query":{"search":[{"pageid":123,"ns":6,"title":"File:Met.jpg"}]}}'
        with patch.object(subject, "request_bytes", side_effect=[busy, busy, success]) as request, patch.object(subject.time, "sleep") as sleep:
            result = subject.search_met_files(10020, "agent", 30)
        self.assertEqual([{"pageid": 123, "title": "File:Met.jpg"}], result)
        self.assertEqual(3, request.call_count)
        self.assertEqual([30, 60], [call.args[0] for call in sleep.call_args_list])

    def test_persistent_search_overload_cooldown_preserves_code(self):
        busy = b'{"error":{"code":"cirrussearch-too-busy-error"}}'
        with patch.object(subject, "request_bytes", return_value=busy), patch.object(subject.time, "sleep"):
            with self.assertRaises(subject.RemoteCooldown) as caught:
                subject.search_met_files(10020, "agent", 30)
        self.assertEqual("cirrussearch-too-busy-error", caught.exception.api_error_code)
        self.assertEqual("commons.wikimedia.org", caught.exception.host)
        self.assertEqual(180, caught.exception.retry_after_seconds)

    def test_nontransient_api_error_is_not_retried(self):
        invalid = b'{"error":{"code":"badvalue","info":"Invalid query"}}'
        with patch.object(subject, "request_bytes", return_value=invalid) as request, patch.object(subject.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "Commons search error"):
                subject.search_met_files(10020, "agent", 30)
        self.assertEqual(1, request.call_count)
        sleep.assert_not_called()

    def test_structured_entity_lookup_retries_maxlag(self):
        busy = b'{"error":{"code":"maxlag","info":"Waiting for replicas"}}'
        good = b'{"entities":{"M123":{"statements":{"P3634":[{"mainsnak":{"datavalue":{"value":"10020"}}}]}}}}'
        with patch.object(subject, "request_bytes", side_effect=[busy,good]), patch.object(subject.time, "sleep") as sleep:
            self.assertTrue(subject.verify_structured_met_id(123, 10020, "agent", 30))
        sleep.assert_called_once_with(30)


class CommonsJpegValidationTests(unittest.TestCase):
    def test_trailing_seft_is_accepted_without_rewriting_source(self):
        original = jpeg_image(b"\x00" * 9103 + b"SEFT")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "original.jpg"
            path.write_bytes(original)
            metadata = subject.sha256_file_verified(path)
            self.assertEqual(len(original), metadata["fileSize"])
            self.assertEqual(original, path.read_bytes())

    def test_truncated_jpeg_missing_eoi_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "truncated.jpg"
            path.write_bytes(jpeg_image()[:-2])
            with self.assertRaisesRegex(ValueError, "end-of-image"):
                subject.sha256_file_verified(path)

    def test_truncated_image_with_eoi_is_rejected_by_decoder(self):
        original = jpeg_image()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corrupt.jpg"
            path.write_bytes(original[:len(original) // 2] + original[-2:])
            with self.assertRaisesRegex(ValueError, "fully decoded"):
                subject.sha256_file_verified(path)

    def test_download_accepts_jpeg_with_trailing_seft(self):
        from unittest.mock import MagicMock
        image = jpeg_image(b"\x00" * 9103 + b"SEFT")
        response = BytesIO(image)
        response.headers = {"Content-Type": "image/jpeg", "Content-Length": str(len(image))}
        opener = MagicMock()
        opener.open.return_value = response
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, "build_opener", return_value=opener), \
             patch.object(subject, "pace_wikimedia"):
            path = Path(directory) / "image.jpg"
            status, metadata = subject.acquire_file({"downloadUrl": "https://upload.wikimedia.org/a.jpg"}, path, "agent", 10, 20000)
            self.assertEqual("DOWNLOADED", status)
            self.assertEqual(image, path.read_bytes())
            self.assertEqual(len(image), metadata["fileSize"])


if __name__ == "__main__":
    unittest.main()
