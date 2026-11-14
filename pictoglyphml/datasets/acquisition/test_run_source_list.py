import unittest
from run_source_list import canonical_url, resolve_sources


class SourceListTest(unittest.TestCase):
    def setUp(self):
        self.catalogue = [
            {"id": "indus-lipi", "landing": "https://github.com/yajnadevam/lipi",
             "download_support": "supported_by_existing_downloader", "state": "ready"},
            {"id": "maya-bonn", "landing": "https://example.org/maya",
             "download_support": "needs_method_or_rights_review", "state": "ready"},
        ]

    def test_exact_catalogue_match(self):
        r = resolve_sources(["https://github.com/yajnadevam/lipi/"], self.catalogue)
        self.assertEqual("PLANNED", r[0]["status"])
        self.assertEqual("indus-lipi", r[0]["source_id"])

    def test_unknown_site_is_not_scraped(self):
        r = resolve_sources(["https://unlisted.example.org"], self.catalogue)
        self.assertEqual("UNRECOGNISED", r[0]["status"])

    def test_known_unsupported_site_needs_adapter(self):
        r = resolve_sources(["https://example.org/maya"], self.catalogue)
        self.assertEqual("NEEDS_ADAPTER", r[0]["status"])

    def test_rejects_non_https(self):
        with self.assertRaises(ValueError):
            canonical_url("http://example.org")

    def test_deduplicates_urls(self):
        r = resolve_sources(["https://github.com/yajnadevam/lipi", "https://github.com/yajnadevam/lipi/"], self.catalogue)
        self.assertEqual(1, len(r))


if __name__ == "__main__":
    unittest.main()
