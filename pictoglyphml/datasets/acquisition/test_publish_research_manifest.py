import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from publish_research_manifest import build_payloads, within_root


def write_csv(path, rows):
    with path.open('w', newline='', encoding='utf-8') as handle:
        w = csv.DictWriter(handle, fieldnames=rows[0].keys())
        w.writeheader()
        w.writerows(rows)


class PublishResearchManifestTest(unittest.TestCase):
    def test_linked_and_unlinked_images_remain_distinct(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            manifest = root / 'manifest'
            manifest.mkdir()
            first, second = root / 'first.jpg', root / 'second.jpg'
            first.write_bytes(b'image bytes')
            second.write_bytes(b'image bytes')
            write_csv(manifest / 'inscriptions.csv', [
                {'source_id':'indus-lipi','inscription_id':'I1','script':'Indus','site':'Harappa',
                 'transcription_status':'SOURCE_TRANSCRIPTION_UNVERIFIED'}
            ])
            write_csv(manifest / 'images.csv', [
                {'source_id':'indus-lipi','inscription_id':'I1','image_path':str(first),
                 'image_relpath':'images/first.jpg','image_kind':'seal_photo',
                 'link_status':'LINKED','image_rights':'REVIEW_SOURCE_LICENCE'},
                {'source_id':'indus-lipi','inscription_id':'I1','image_path':str(second),
                 'image_relpath':'images/second.jpg','image_kind':'seal_photo',
                 'link_status':'REVIEW_LINK','image_rights':'REVIEW_SOURCE_LICENCE'}
            ])
            with patch('publish_research_manifest.CATALOGUE', root/'catalogue.json'):
                (root/'catalogue.json').write_text(json.dumps({'sources':[
                    {'id':'indus-lipi','landing':'https://example.org/indus'}]}))
                records, stats = build_payloads(manifest, root)
            self.assertEqual(2, len(records))
            linked = next(r for r in records if r['recordType'] == 'INSCRIPTION')
            unlinked = next(r for r in records if r['recordType'] == 'IMAGE_ONLY')
            self.assertEqual(1, len(linked['images']))
            self.assertEqual('REVIEW_LINK', unlinked['images'][0]['analysis']['linkStatus'])
            self.assertEqual('', unlinked['archaeologicalContext']['inscription_id'])
            self.assertIsNone(linked['images'][0]['analysis']['verifiedSignLabel'])
            self.assertEqual(1, stats['duplicate_images'])

    def test_rejects_path_outside_research_root(self):
        with tempfile.TemporaryDirectory() as one, tempfile.TemporaryDirectory() as two:
            other = Path(two) / 'image.png'
            other.write_bytes(b'image')
            with self.assertRaises(ValueError):
                within_root(str(other), Path(one), 100)


if __name__ == '__main__':
    unittest.main()
