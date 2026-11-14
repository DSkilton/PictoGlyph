import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from met_catalogue_enumerator import classify, confirmed_public_domain, scan_csv, sha256_file, sha256_json, validate_csv


class MetCatalogueEnumeratorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.csv_path = self.root / "MetObjects.csv"
        self.rows = [
            {"Object ID": "101", "Is Public Domain": "True", "Department": "Egyptian Art", "Title": "Inlay, hieroglyph", "Object Name": "Inlay"},
            {"Object ID": "102", "Is Public Domain": "False", "Department": "Ancient Near Eastern Art", "Title": "Clay seal", "Object Name": "Seal"},
            {"Object ID": "103", "Is Public Domain": "True", "Department": "European Paintings", "Title": "Landscape", "Object Name": "Painting"},
            {"Object ID": "999", "Is Public Domain": "True", "Department": "Egyptian Art", "Title": "Cuneiform tablet", "Object Name": "Tablet"},
        ]
        with self.csv_path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(self.rows[0]))
            writer.writeheader()
            writer.writerows(self.rows)
        self.ids = {101, 102, 103, 104}
        self.ids_hash = sha256_json(sorted(self.ids))
        self.csv_hash = sha256_file(self.csv_path)

    def tearDown(self):
        self.tmp.cleanup()

    def scan(self, max_rows=None, reset=False):
        return scan_csv(self.root, self.csv_path, self.ids, self.ids_hash, self.csv_hash,
                        max_rows=max_rows, checkpoint_interval=1, reset=reset)

    def test_strong_match_and_weak_match_are_distinct(self):
        self.assertEqual("HIGH", classify(self.rows[0])[0])
        self.assertEqual("REVIEW", classify(self.rows[1])[0])
        self.assertIsNone(classify(self.rows[2])[0])

    def test_rights_are_recorded_not_silently_assumed(self):
        self.assertTrue(confirmed_public_domain(self.rows[0]))
        self.assertFalse(confirmed_public_domain(self.rows[1]))

    def test_full_scan_reports_coverage_and_never_invents_sign_labels(self):
        report = self.scan()
        self.assertEqual("COMPLETE", report["status"])
        self.assertEqual(4, report["allCurrentApiObjectIds"])
        self.assertEqual(3, report["currentApiIdsCoveredByCsv"])
        self.assertEqual(1, report["currentApiIdsMissingFromCsv"])
        self.assertEqual(2, report["candidateRecords"])
        candidates = [json.loads(line) for line in (self.root / "met-candidates.jsonl").read_text().splitlines()]
        self.assertEqual([101, 102], [item["objectId"] for item in candidates])
        self.assertEqual("UNVERIFIED_POTENTIAL_INSCRIPTION", candidates[0]["interpretationStatus"])
        self.assertEqual(["104"], (self.root / "met-current-ids-not-in-csv.txt").read_text().splitlines())

    def test_resume_uses_checkpoint_without_duplicates(self):
        partial = self.scan(max_rows=2)
        self.assertEqual("PARTIAL", partial["status"])
        report = self.scan()
        self.assertEqual("COMPLETE", report["status"])
        self.assertEqual(2, len((self.root / "met-candidates.jsonl").read_text().splitlines()))
        self.scan()
        self.assertEqual(2, len((self.root / "met-candidates.jsonl").read_text().splitlines()))

    def test_checkpoint_rejects_modified_input(self):
        self.scan(max_rows=2)
        with self.assertRaisesRegex(ValueError, "snapshots or rules changed"):
            scan_csv(self.root, self.csv_path, self.ids, self.ids_hash, "changed", checkpoint_interval=1)

    def test_duplicate_csv_ids_do_not_duplicate_candidates(self):
        with self.csv_path.open("a", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(self.rows[0]))
            writer.writerow(self.rows[0])
        self.csv_hash = sha256_file(self.csv_path)
        report = self.scan()
        self.assertEqual(2, report["candidateRecords"])
        self.assertEqual(1, report["duplicateCsvIds"])

    def test_git_lfs_pointer_is_not_a_dataset(self):
        self.csv_path.write_text("version https://git-lfs.github.com/spec/v1\noid sha256:abcd\nsize 5\n")
        with self.assertRaisesRegex(ValueError, "Git LFS pointer"):
            validate_csv(self.csv_path)


if __name__ == "__main__":
    unittest.main()
