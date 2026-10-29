#!/usr/bin/env python3
"""Export source-annotated archaeological image regions for *exploratory* ML.

Input: PictoGlyph ``provisional_crops.csv`` from build_multiscript_manifest.py.
Output: original-size PNG crops, individual provenance rows, and a summary.

This is NOT a supervised training-set builder. A geometrically valid bounding box
is not a confirmed symbol, and source annotations are not ground-truth labels.
Original images are read-only; no files are deleted or changed.

Example:
  python export_provisional_glyph_crops.py --limit 200
  python export_provisional_glyph_crops.py --limit 0
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

from PIL import Image, UnidentifiedImageError

DATA_ROOT = Path('/mnt/i/PictoGlyph/data/research-datasets')
DEFAULT_MANIFEST = DATA_ROOT / 'manifests/multiscript-v1/provisional_crops.csv'
DEFAULT_OUTPUT = DATA_ROOT / 'crops/cretan-provisional-v1'
REQUIRED = {'source_id', 'inscription_id', 'image_path', 'region_index',
            'x', 'y', 'width', 'height'}
OUT_FIELDS = [
    'crop_id', 'source_id', 'script', 'inscription_id', 'split_group',
    'source_image_path', 'crop_path', 'source_annotation', 'source_region_index',
    'x', 'y', 'width', 'height', 'original_width', 'original_height',
    'source_region_status', 'source_label', 'sign_identity_status',
    'review_verdict', 'review_notes', 'image_modality', 'crop_sha256', 'status',
    'error',
]


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open('r', encoding='utf-8-sig', newline='') as f:
        reader = csv.DictReader(f)
        if not REQUIRED.issubset(reader.fieldnames or []):
            raise ValueError(f'{path}: missing required columns {sorted(REQUIRED - set(reader.fieldnames or []))}')
        return list(reader)


def key(r: dict[str, str]) -> tuple[str, ...]:
    return tuple(str(r.get(k, '')) for k in (
        'source_id', 'inscription_id', 'image_path', 'region_index',
        'x', 'y', 'width', 'height'
    ))


def load_reviews(path: Path | None) -> dict[tuple[str, ...], dict[str, str]]:
    if path is None:
        return {}
    return {key(row): row for row in read_csv(path)}


def stable_id(r: dict[str, str]) -> str:
    # Stable within a given source image path and annotation release.
    digest = hashlib.sha256(json.dumps(key(r), ensure_ascii=False).encode('utf-8')).hexdigest()[:20]
    return f'{r["source_id"]}-{digest}'


def image_modality(path: Path) -> str:
    name = path.stem.lower()
    if 'facsimile' in name:
        return 'FACSIMILE'
    if 'photo' in name:
        return 'PHOTOGRAPH'
    return 'OTHER_OR_UNKNOWN'


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def export_row(row: dict[str, str], out_dir: Path, reviews: dict,
               dry_run: bool, overwrite: bool) -> dict[str, str]:
    source = Path(row['image_path'])
    crop_id = stable_id(row)
    dest = out_dir / 'images' / row['source_id'] / row['inscription_id'] / f'{crop_id}.png'
    review = reviews.get(key(row), {})
    result = {
        'crop_id': crop_id, 'source_id': row['source_id'],
        'script': row.get('script', ''), 'inscription_id': row['inscription_id'],
        'split_group': f'{row["source_id"]}:{row["inscription_id"]}',
        'source_image_path': str(source), 'crop_path': str(dest),
        'source_annotation': row.get('source_annotation', ''),
        'source_region_index': row['region_index'],
        **{k: row.get(k, '') for k in ('x', 'y', 'width', 'height')},
        'original_width': '', 'original_height': '',
        'source_region_status': row.get('region_status', ''),
        'source_label': row.get('label', ''),
        'sign_identity_status': 'NOT_VERIFIED',
        'review_verdict': review.get('review_verdict', ''),
        'review_notes': review.get('review_notes', ''),
        'image_modality': image_modality(source),
        'crop_sha256': '', 'status': '', 'error': '',
    }
    try:
        x, y, w, h = (int(row[k]) for k in ('x', 'y', 'width', 'height'))
        if x < 0 or y < 0 or w < 1 or h < 1:
            raise ValueError('Invalid coordinates: negative origin or zero-size region')
        with Image.open(source) as original:
            image_width, image_height = original.size
            result['original_width'], result['original_height'] = image_width, image_height
            if x + w > image_width or y + h > image_height:
                raise ValueError('Box extends outside source image')
            if dry_run:
                result['status'] = 'VALIDATED_DRY_RUN'
                return result
            if dest.exists() and not overwrite:
                # Do not silently overwrite an existing file; still record content hash.
                result['status'] = 'EXISTS'
            else:
                # Preserve original pixel dimensions. Do not preprocess or resize.
                crop = original.crop((x, y, x + w, y + h))
                dest.parent.mkdir(parents=True, exist_ok=True)
                crop.save(dest, format='PNG')
                result['status'] = 'CREATED'
        result['crop_sha256'] = sha256_file(dest)
    except (ValueError, KeyError, FileNotFoundError, PermissionError, UnidentifiedImageError, OSError) as e:
        result['status'] = 'ERROR'
        result['error'] = f'{type(e).__name__}: {e}'[:450]
    return result


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest', type=Path, default=DEFAULT_MANIFEST)
    p.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    p.add_argument('--review-csv', type=Path,
                   help='Optional reviewed crop_review_labels.csv (review notes only, not sign identities)')
    p.add_argument('--limit', type=int, default=200,
                   help='First N regions in deterministic manifest order; 0 means all (default: 200)')
    p.add_argument('--dry-run', action='store_true')
    p.add_argument('--overwrite', action='store_true')
    args = p.parse_args()
    if args.limit < 0:
        p.error('--limit must be zero or positive')
    if not args.manifest.is_file():
        p.error(f'Manifest does not exist: {args.manifest}')
    if args.review_csv and not args.review_csv.is_file():
        p.error(f'Review file does not exist: {args.review_csv}')
    rows = read_csv(args.manifest)
    chosen = rows[:args.limit] if args.limit else rows
    reviews = load_reviews(args.review_csv)
    args.output.mkdir(parents=True, exist_ok=True)
    counts = Counter()
    outputs = []
    for i, row in enumerate(chosen, 1):
        item = export_row(row, args.output, reviews, args.dry_run, args.overwrite)
        outputs.append(item)
        counts[item['status']] += 1
        if i % 250 == 0 or i == len(chosen):
            print(f'{i}/{len(chosen)} {dict(counts)}', flush=True)
    out_csv = args.output / ('crop_manifest_dry_run.csv' if args.dry_run else 'crop_manifest.csv')
    with out_csv.open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, OUT_FIELDS)
        writer.writeheader()
        writer.writerows(outputs)
    summary = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'input_manifest': str(args.manifest),
        'source_rows': len(rows), 'selected_rows': len(chosen),
        'dry_run': args.dry_run, 'status_counts': dict(counts),
        'review_annotations_matched': sum(bool(v['review_verdict']) for v in outputs),
        'sign_identities_verified_by_this_script': 0,
        'crop_manifest': str(out_csv),
        'note': 'These are source-annotated PROVISIONAL regions, not verified single signs or classifications. Split experiments by inscription (split_group).',
    }
    (args.output / 'crop_export_summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2), flush=True)
    return 0 if not counts['ERROR'] else 1


if __name__ == '__main__':
    sys.exit(main())
