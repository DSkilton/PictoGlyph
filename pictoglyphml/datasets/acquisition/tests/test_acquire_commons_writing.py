import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import acquire_commons_writing as subject

IMAGE = b'\xff\xd8\xffexample\xff\xd9'


def response(licence='https://creativecommons.org/licenses/by/4.0/', artist='Example photographer'):
    return {'query': {'pages': [{'pageid': 42, 'ns': 6, 'title': 'File:Inscription.jpg', 'imageinfo': [{'mime': 'image/jpeg', 'size': len(IMAGE), 'sha1': hashlib.sha1(IMAGE).hexdigest(), 'url': 'https://upload.wikimedia.org/wikipedia/commons/a/ab/Inscription.jpg', 'descriptionurl': 'https://commons.wikimedia.org/wiki/File:Inscription.jpg', 'extmetadata': {'LicenseUrl': {'value': licence}, 'Artist': {'value': artist}}}]}]}}


def args():
    return SimpleNamespace(script='greek', medium='gravestone', limit=2, user_agent='test', timeout=10, max_image_mb=1)


class CommonsWritingTests(unittest.TestCase):
    def test_all_target_scripts_have_query_presets(self):
        for script in ('latin', 'greek', 'ancient-greek', 'cyrillic', 'arabic', 'hebrew', 'bengali', 'devanagari', 'han', 'japanese', 'hangul', 'coptic'):
            self.assertIn('gravestone', subject.load_query(script, 'gravestone'))
        with self.assertRaises(ValueError):
            subject.load_query('unrecognised', 'gravestone')

    def test_license_allowlist_checks_exact_host_and_path(self):
        self.assertEqual('CC-BY-SA-4.0', subject.license_id('https://creativecommons.org/licenses/by-sa/4.0/'))
        self.assertEqual('CC0-1.0', subject.license_id('https://creativecommons.org/publicdomain/zero/1.0/'))
        for url in ('https://evil.invalid/licenses/by/4.0/', 'https://creativecommons.org.evil.invalid/licenses/by/4.0/', 'https://creativecommons.org/licenses/by-nc/4.0/', 'https://creativecommons.org/licenses/by/4.0/?x=1', ''):
            with self.subTest(url=url), self.assertRaises(ValueError):
                subject.license_id(url)

    def test_imageinfo_preserves_raw_metadata_without_approving_training(self):
        info = subject.parse_imageinfo(response(), 42, 1000)
        self.assertEqual('CC-BY-4.0', info['license'])
        self.assertEqual('Example photographer', info['sourceMetadata']['Artist']['value'])
        self.assertFalse(info['trainingDataApproved'])
        self.assertNotIn('script', info)

    def test_identity_size_attribution_and_host_are_checked(self):
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(response(), 43, 1000)
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(response(), 42, 1)
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(response(artist=''), 42, 1000)
        data = response()
        data['query']['pages'][0]['imageinfo'][0]['url'] = 'https://upload.wikimedia.org.evil.invalid/file.jpg'
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(data, 42, 1000)

    def test_non_jpeg_and_additional_restrictions_are_rejected(self):
        data = response()
        data['query']['pages'][0]['imageinfo'][0]['mime'] = 'image/png'
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(data, 42, 1000)
        data = response()
        data['query']['pages'][0]['imageinfo'][0]['extmetadata']['Restrictions'] = {'value': 'other rights'}
        with self.assertRaises(ValueError):
            subject.parse_imageinfo(data, 42, 1000)

    def test_search_paginates_and_deduplicates(self):
        pages = [{'query': {'search': [{'pageid': 42, 'ns': 6}]}, 'continue': {'continue': '-||', 'sroffset': 1}}, {'query': {'search': [{'pageid': 42, 'ns': 6}, {'pageid': 43, 'ns': 6}]}}]
        with patch.object(subject.commons, 'commons_query', side_effect=pages) as query:
            self.assertEqual([42, 43], subject.search('Greek gravestone', 2, 'test', 10))
        self.assertEqual(1, query.call_args.args[0]['sroffset'])

    def test_upstream_hash_and_size_are_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'image.jpg'
            path.write_bytes(IMAGE)
            info = subject.parse_imageinfo(response(), 42, 1000)
            self.assertEqual(hashlib.sha256(IMAGE).hexdigest(), subject.verify_upstream(path, info))
            path.write_bytes(IMAGE[:-1] + b'x')
            with self.assertRaises(ValueError):
                subject.verify_upstream(path, info)

    def test_preview_never_downloads(self):
        info = subject.parse_imageinfo(response(), 42, 1000)
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, 'search', return_value=[42]), patch.object(subject, 'imageinfo', return_value=info), patch.object(subject.commons, 'acquire_file') as acquire:
            root = Path(directory)
            self.assertEqual(0, subject.preview(args(), root))
            acquire.assert_not_called()
            plan = json.loads((root / 'commons-writing-plan.json').read_text())
            self.assertEqual('PREVIEW_READY', plan['candidates'][0]['status'])
            self.assertFalse(plan['trainingDataApproved'])
            self.assertFalse((root / 'images').exists())
            with self.assertRaises(ValueError):
                subject.preview(args(), root)

    def test_download_rechecks_metadata_and_refuses_changed_licence(self):
        info = subject.parse_imageinfo(response(), 42, 1000)
        changed = subject.parse_imageinfo(response('https://creativecommons.org/licenses/by-sa/4.0/'), 42, 1000)
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, 'search', return_value=[42]), patch.object(subject, 'imageinfo', side_effect=[info, changed]), patch.object(subject.commons, 'acquire_file') as acquire:
            root = Path(directory)
            subject.preview(args(), root)
            self.assertEqual(1, subject.download(args(), root))
            acquire.assert_not_called()
            audit = json.loads((root / 'commons-writing-audit.jsonl').read_text())
            self.assertEqual('FAILED', audit['status'])

    def test_download_writes_sidecar_then_reuses_without_overwriting(self):
        info = subject.parse_imageinfo(response(), 42, 1000)
        def acquire(current, destination, agent, timeout, maximum):
            if destination.exists():
                return 'REUSED', json.loads(destination.with_suffix('.json').read_text())
            destination.write_bytes(IMAGE)
            return 'DOWNLOADED', {'sha256': hashlib.sha256(IMAGE).hexdigest(), 'fileSize': len(IMAGE), **current}
        with tempfile.TemporaryDirectory() as directory, patch.object(subject, 'search', return_value=[42]), patch.object(subject, 'imageinfo', return_value=info), patch.object(subject.commons, 'acquire_file', side_effect=acquire):
            root = Path(directory)
            subject.preview(args(), root)
            self.assertEqual(0, subject.download(args(), root))
            sidecar = root / 'images/42.json'
            original = sidecar.read_bytes()
            self.assertFalse(json.loads(original)['trainingDataApproved'])
            self.assertEqual(0, subject.download(args(), root))
            self.assertEqual(original, sidecar.read_bytes())


if __name__ == '__main__':
    unittest.main()
