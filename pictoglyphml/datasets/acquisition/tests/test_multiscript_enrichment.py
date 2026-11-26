import json
import sys
import tempfile
import unittest
from pathlib import Path

ACQUISITION = Path(__file__).resolve().parents[1]
EXPERIMENTS = ACQUISITION.parents[1] / "experiments"
sys.path.insert(0, str(EXPERIMENTS))

from enrich_writing_metadata import assess, select_images, PROMPTS


class MetadataEnrichmentTests(unittest.TestCase):
    def test_candidate_is_never_verified(self):
        result = assess({"stone": 0.9, "paper": 0.1, "unknown": 0.2}, 0.5, 0.06)
        self.assertEqual("stone", result["candidate"])
        self.assertEqual("UNVERIFIED", result["status"])
        self.assertEqual("AI_INFERRED", result["origin"])
        self.assertEqual(0.1, result["scores"]["paper"])

    def test_ambiguous_scores_abstain(self):
        result = assess({"Greek": 0.91, "Latin": 0.90}, 0.5, 0.06)
        self.assertIsNone(result["candidate"])
        self.assertEqual("ABSTAINED", result["status"])

    def test_low_score_or_unknown_abstains(self):
        self.assertIsNone(assess({"stone": 0.3, "paper": 0.1}, 0.5, 0.06)["candidate"])
        self.assertIsNone(assess({"stone": 0.2, "unknown": 0.9}, 0.5, 0.06)["candidate"])

    def test_invalid_scores_rejected(self):
        for bad in (float("nan"), float("inf"), -0.1, 1.1):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                assess({"stone": bad, "paper": 0.2}, 0.5, 0.06)

    def test_sampling_is_reproducible_and_bounded(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for index in range(10):
                (root / f"{index}.png").touch()
            (root / "metadata.json").touch()
            (root / "character.svg").touch()
            first = select_images(root, 3, 17)
            self.assertEqual(first, select_images(root, 3, 17))
            self.assertEqual(3, len(first))
            self.assertTrue(all(p.suffix == ".png" for p in first))

    def test_every_prompt_group_supports_unknown(self):
        self.assertTrue(all("unknown" in labels for labels in PROMPTS.values()))


class MultiscriptCatalogueTests(unittest.TestCase):
    def test_new_catalogue_sources_are_distinct_and_pinned(self):
        catalogue = json.loads((ACQUISITION / "sources.json").read_text(encoding="utf-8"))
        entries = {s["id"]: s for s in catalogue["sources"]}
        self.assertEqual(len(entries), len(catalogue["sources"]))
        self.assertEqual(len(entries), catalogue["source_count"])
        for source_id in ("omniglot-background", "omniglot-evaluation"):
            entry = entries[source_id]
            self.assertEqual("http", entry["method"])
            self.assertEqual("ready", entry["state"])
            self.assertIn("057f034baf2ecb8530bc5710e5a23584d2a519cc", entry["url"])
        self.assertIn("/17.0.0/", entries["unicode-ucd-17"]["url"])


if __name__ == "__main__":
    unittest.main()
