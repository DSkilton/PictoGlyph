import hashlib
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from acquisition_io import append_jsonl, sha256_file, utc_now, write_json_atomic
from acquisition_constants import CHUNK_SIZE_BYTES


class AcquisitionIOTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def test_checksum_covers_multiple_chunks(self):
        path = self.root / "image.bin"
        data = b"a" * CHUNK_SIZE_BYTES + b"tail"
        path.write_bytes(data)
        self.assertEqual(hashlib.sha256(data).hexdigest(), sha256_file(path))

    def test_timestamp_is_utc(self):
        timestamp = datetime.fromisoformat(utc_now())
        self.assertEqual(timezone.utc, timestamp.tzinfo)

    def test_atomic_json_preserves_unicode_and_replaces_existing_file(self):
        path = self.root / "checkpoint.json"
        write_json_atomic(path, {"step": 1})
        write_json_atomic(path, {"step": 2, "script": "Ελληνικά"})
        self.assertEqual({"step": 2, "script": "Ελληνικά"}, json.loads(path.read_text(encoding="utf-8")))
        self.assertEqual([path], list(self.root.iterdir()))

    def test_failed_json_serialisation_preserves_checkpoint_and_cleans_temp(self):
        path = self.root / "checkpoint.json"
        write_json_atomic(path, {"step": 1})
        with self.assertRaises(TypeError):
            write_json_atomic(path, {"step": object()})
        self.assertEqual({"step": 1}, json.loads(path.read_text(encoding="utf-8")))
        self.assertEqual([path], list(self.root.iterdir()))

    def test_failed_replace_preserves_checkpoint_and_cleans_temp(self):
        path = self.root / "checkpoint.json"
        write_json_atomic(path, {"step": 1})
        with patch.object(Path, "replace", side_effect=OSError("disk error")):
            with self.assertRaises(OSError):
                write_json_atomic(path, {"step": 2})
        self.assertEqual({"step": 1}, json.loads(path.read_text(encoding="utf-8")))
        self.assertEqual([path], list(self.root.iterdir()))

    def test_jsonl_appends_without_overwriting(self):
        path = self.root / "audit.jsonl"
        append_jsonl(path, {"step": 1})
        append_jsonl(path, {"step": 2})
        self.assertEqual([{"step": 1}, {"step": 2}], [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()])


if __name__ == "__main__":
    unittest.main()
