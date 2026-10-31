import csv
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from build_mmm_manifest import build_manifest, describe_image


class MmmManifestTest(unittest.TestCase):
    def test_filename_parsing_and_provisional_status(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'MMM'
            folder = root / 'STN'
            folder.mkdir(parents=True)
            path = folder / 'STN1_0_G5.png'
            Image.new('RGB', (20, 12), 'white').save(path)
            item = describe_image(root, path)
            self.assertEqual(item['gardiner_label'], 'G5')
            self.assertEqual(item['source_document'], 'STN1')
            self.assertEqual(item['style'], 'STN')
            self.assertEqual(item['review_status'], 'PENDING')
            self.assertEqual(item['label_status'], 'SOURCE_PROVIDED_UNVERIFIED')

    def test_manifest_flags_bad_filenames_and_duplicate_pixels(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            root = base / 'MMM'
            folder = root / 'ABD'
            folder.mkdir(parents=True)
            for name in ('ABD1_0_A1.png', 'ABD1_1_A1.png', 'bad.png'):
                Image.new('RGB', (12, 12), 'white').save(folder / name)
            result = build_manifest(root, base / 'reports')
            self.assertEqual(result['total_crops'], 2)
            self.assertEqual(result['rejected_files'], 1)
            self.assertEqual(result['duplicate_files'], 1)
            with (base / 'reports' / 'manifest.csv').open() as f:
                rows = list(csv.DictReader(f))
            self.assertEqual(rows[0]['source_document'], 'ABD1')
            self.assertEqual(rows[1]['duplicate_of'], rows[0]['relative_path'])

if __name__ == '__main__':
    unittest.main()
