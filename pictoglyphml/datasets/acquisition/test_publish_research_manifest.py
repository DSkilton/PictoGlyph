import csv
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

from publish_research_manifest import build_payloads, report_progress, within_root


def write_csv(path, rows):
    with path.open('w', newline='', encoding='utf-8') as handle:
        w = csv.DictWriter(handle, fieldnames=rows[0].keys())
        w.writeheader()
        w.writerows(rows)


class PublishResearchManifestTest(unittest.TestCase):
    def test_progress_reports_every_interval_or_batch(self):
        output = StringIO()
        with redirect_stdout(output), patch('publish_research_manifest.time.monotonic', return_value=25):
            last = report_progress('Images', 101, 1000, 0, 0)
        self.assertEqual(25, last)
        self.assertIn('Images: 101/1,000 (10.1%)', output.getvalue())
        output = StringIO()
        with redirect_stdout(output), patch('publish_research_manifest.time.monotonic', return_value=26):
            last = report_progress('Images', 102, 1000, 0, 25)
        self.assertEqual(25, last)
        self.assertEqual('', output.getvalue())

    
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
                 'transcription_status':'SOURCE_TRANSCRIPTION_UNVERIFIED',
                 'source_record_reference':'records.csv:id=1'}
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

    
    def test_duplicate_inscription_identifiers_preserve_all_source_rows(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            manifest = root / 'manifest'
            manifest.mkdir()
            image = root / 'photo.jpg'
            image.write_bytes(b'seal image')
            write_csv(manifest / 'inscriptions.csv', [
                {'source_id': 'indus-lipi', 'inscription_id': '-', 'script': 'Indus',
                 'source_record_reference': 'inscriptions.csv:id=101'},
                {'source_id': 'indus-lipi', 'inscription_id': '-', 'script': 'Indus',
                 'source_record_reference': 'inscriptions.csv:id=102'},
                {'source_id': 'indus-lipi', 'inscription_id': 'H-111', 'script': 'Indus',
                 'source_record_reference': 'inscriptions.csv:id=103'},
                {'source_id': 'indus-lipi', 'inscription_id': 'H-111', 'script': 'Indus',
                 'source_record_reference': 'inscriptions.csv:id=104'}
            ])
            write_csv(manifest / 'images.csv', [
                {'source_id': 'indus-lipi', 'inscription_id': 'H-111', 'image_path': str(image),
                 'image_relpath': 'photo.jpg', 'image_kind': 'seal_image', 'link_status': 'LINKED'}
            ])
            catalogue = root / 'catalogue.json'
            catalogue.write_text(json.dumps({'sources': [{'id':'indus-lipi', 'landing':'https://example.org/indus'}]}))
            with patch('publish_research_manifest.CATALOGUE', catalogue):
                records, stats = build_payloads(manifest, root)
            original = [r for r in records if r['recordType'] == 'INSCRIPTION']
            isolated = [r for r in records if r['recordType'] == 'IMAGE_ONLY']
            self.assertEqual(4, len(original))
            self.assertEqual(4, len({r['recordKey'] for r in original}))
            self.assertEqual(1, len(isolated))
            self.assertEqual(1, stats['unlinked_images'])
            self.assertEqual('AMBIGUOUS_RECORD_ID', isolated[0]['images'][0]['analysis']['linkStatus'])

    
    def test_rejects_duplicate_source_record_references(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            manifest = root / 'manifest'
            manifest.mkdir()
            write_csv(manifest / 'inscriptions.csv', [
                {'source_id': 'indus-lipi', 'inscription_id': 'A', 'source_record_reference': 'same'},
                {'source_id': 'indus-lipi', 'inscription_id': 'B', 'source_record_reference': 'same'}
            ])
            write_csv(manifest / 'images.csv', [
                {'source_id': 'indus-lipi', 'inscription_id': 'A', 'image_path': 'unused', 'image_relpath': 'unused'}
            ])
            with patch('publish_research_manifest.CATALOGUE', root / 'catalogue.json'):
                (root / 'catalogue.json').write_text(json.dumps({'sources':[{'id':'indus-lipi','landing':'https://example.org'}]}))
                with self.assertRaisesRegex(ValueError, 'Duplicate source-record reference'):
                    build_payloads(manifest, root)

    
    def test_rejects_path_outside_research_root(self):
        with tempfile.TemporaryDirectory() as one, tempfile.TemporaryDirectory() as two:
            other = Path(two) / 'image.png'
            other.write_bytes(b'image')
            with self.assertRaises(ValueError):
                within_root(str(other), Path(one), 100)


if __name__ == '__main__':
    unittest.main()
