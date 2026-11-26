"""Focused tests for the Met bulk-import checkpoint and replay behavior."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'bulk_import_met.py'
if not SCRIPT.is_file():
    raise unittest.SkipTest('Legacy query-page importer bulk_import_met.py is absent; exact-ID import tests remain active')

spec = importlib.util.spec_from_file_location('bulk_import_met', SCRIPT)
met = importlib.util.module_from_spec(spec)
spec.loader.exec_module(met)


class MetBulkImportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.args = SimpleNamespace(output=Path(self.temp.name), execute=True, page_size=20,
                                    api_url='http://127.0.0.1:8080', max_pages=None, delay=0)

    def test_imports_all_pages_and_resume_does_not_replay_completed_pages(self):
        calls = []

        def fake(base, token, query, limit, offset, operation):
            calls.append((operation, offset))
            if operation == 'preview':
                return {'totalMatches': 21, 'candidates': [{'imageUrls': ['https://example.org/a.jpg']}] * (20 if offset == 0 else 1)}
            return {'candidatesFound': 20 if offset == 0 else 1, 'recordsCreated': 20 if offset == 0 else 1,
                    'recordsReused': 0, 'imageReferencesCreated': 20 if offset == 0 else 1, 'skippedWithoutIdentity': 0}

        with patch.object(met, 'request_json', side_effect=fake), patch.object(met.time, 'sleep'):
            self.assertEqual(0, met.run_query(self.args, 'test-token', 'hieroglyph'))
            self.assertEqual(0, met.run_query(self.args, 'test-token', 'hieroglyph'))
        self.assertEqual([('preview', 0), ('import', 0), ('preview', 20), ('import', 20)], calls)
        states = list(self.args.output.rglob('checkpoint.json'))
        state = json.loads(states[0].read_text(encoding='utf-8'))
        self.assertEqual(21, state['recordsCreated'])
        self.assertEqual(40, state['nextOffset'])

    def test_failed_page_stays_available_for_retry(self):
        attempts = []
        failure = True

        def fake(base, token, query, limit, offset, operation):
            nonlocal failure
            attempts.append((operation, offset))
            if operation == 'preview':
                return {'totalMatches': 25, 'candidates': [{}] * (20 if offset == 0 else 5)}
            if offset == 20 and failure:
                failure = False
                raise RuntimeError('Upstream failed')
            return {'candidatesFound': 20 if offset == 0 else 5, 'recordsCreated': 0,
                    'recordsReused': 20 if offset == 0 else 5, 'imageReferencesCreated': 0, 'skippedWithoutIdentity': 0}

        with patch.object(met, 'request_json', side_effect=fake), patch.object(met.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'Upstream failed'):
                met.run_query(self.args, 'test-token', 'hieroglyph')
            state = json.loads(next(self.args.output.rglob('checkpoint.json')).read_text())
            self.assertEqual(20, state['nextOffset'])
            self.assertEqual(0, met.run_query(self.args, 'test-token', 'hieroglyph'))
        self.assertEqual(1, attempts.count(('import', 0)))
        self.assertEqual(2, attempts.count(('import', 20)))

    def test_preview_only_has_distinct_checkpoint_and_never_posts(self):
        self.args.execute = False
        with patch.object(met, 'request_json', return_value={'totalMatches': 1, 'candidates': [{'imageUrls': []}]}) as call:
            self.assertEqual(0, met.run_query(self.args, 'test-token', 'hieroglyph'))
        self.assertEqual('preview', call.call_args.args[-1])
        self.assertTrue(next(self.args.output.rglob('preview/checkpoint.json')).exists())

    def test_stops_when_met_total_changes_between_pages(self):
        def fake(base, token, query, limit, offset, operation):
            if operation == 'preview':
                return {'totalMatches': 41 if offset == 0 else 42, 'candidates': [{}] * 20}
            return {'candidatesFound': 20, 'recordsCreated': 20, 'recordsReused': 0,
                    'imageReferencesCreated': 0, 'skippedWithoutIdentity': 0}

        with patch.object(met, 'request_json', side_effect=fake), patch.object(met.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'result count changed'):
                met.run_query(self.args, 'test-token', 'hieroglyph')
        state = json.loads(next(self.args.output.rglob('checkpoint.json')).read_text())
        self.assertEqual(20, state['nextOffset'])

    def test_reports_search_window_limit_instead_of_claiming_full_coverage(self):
        self.args.execute = False
        self.args.max_pages = 1
        checkpoint_folder = self.args.output / 'hieroglyph-ca87ca8370' / 'preview'
        checkpoint_folder.mkdir(parents=True)
        met.save_json(checkpoint_folder / 'checkpoint.json', {'sourceId': met.SOURCE_ID, 'query': 'hieroglyph',
            'pageSize': 20, 'mode': 'PREVIEW', 'nextOffset': 9980, 'pagesCompleted': 499,
            'recordsCreated': 0, 'recordsReused': 0, 'imageReferencesCreated': 0,
            'skipped': 0, 'totalMatches': 10001})
        with patch.object(met, 'request_json', return_value={'totalMatches': 10001, 'candidates': [{}] * 20}):
            self.assertEqual(2, met.run_query(self.args, 'test-token', 'hieroglyph'))


if __name__ == '__main__':
    unittest.main()
