#!/usr/bin/env python3
"""Create a read-only inventory of MMM hieroglyph crops and source-provided labels."""
import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from PIL import Image

STYLES = {'ABD', 'BAH', 'BIO', 'DEN', 'GLH', 'MED', 'SPL', 'STH', 'STN', 'UNC'}
PATTERN = re.compile(r'^(?P<document>.+)_(?P<index>\d+)_(?P<label>[A-Za-z]{1,2}\d+[A-Za-z]?)$')
FIELDS = ['source', 'relative_path', 'style', 'source_document', 'sign_index',
          'gardiner_label', 'content_type', 'label_status', 'review_status',
          'width', 'height', 'sha256', 'duplicate_of']


def describe_image(root: Path, path: Path) -> dict:
    relative = path.relative_to(root)
    
    if len(relative.parts) != 2 or relative.parts[0] not in STYLES:
        raise ValueError('Unexpected writing-style folder or nested path')
        
    match = PATTERN.fullmatch(path.stem)
    
    if match is None:
        raise ValueError('Filename does not encode document, index and Gardiner label')
    
    with Image.open(path) as image:
        width, height = image.size
        image.verify()
    
    if width <= 0 or height <= 0:
        raise ValueError('Empty image')
    
    hasher = hashlib.sha256()
    
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            hasher.update(chunk)
    
    return dict(source='MMM', relative_path=relative.as_posix(),
                style=relative.parts[0], source_document=match['document'],
                sign_index=int(match['index']), gardiner_label=match['label'],
                content_type='SIGN_CROP', label_status='SOURCE_PROVIDED_UNVERIFIED',
                review_status='PENDING', width=width, height=height,
                sha256=hasher.hexdigest(), duplicate_of='')


def build_manifest(root: Path, output: Path) -> dict:
    if not root.is_dir():
        raise FileNotFoundError(f'MMM folder does not exist: {root}')
    
    rows, issues, seen = [], [], {}
    
    for path in sorted(root.rglob('*.png')):
        try:
            row = describe_image(root, path)
            row['duplicate_of'] = seen.setdefault(row['sha256'], row['relative_path'])
            if row['duplicate_of'] == row['relative_path']:
                row['duplicate_of'] = ''
            rows.append(row)
        except (ValueError, OSError) as exc:
            issues.append(dict(path=str(path.relative_to(root)), problem=str(exc)))
    
    if not rows:
        raise ValueError('No valid MMM PNG crops found')
    
    if output.resolve().is_relative_to(root.resolve()):
        raise ValueError('Output must be outside the original MMM image directory')
    
    output.mkdir(parents=True, exist_ok=True)
    
    with (output / 'manifest.csv').open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    
    with (output / 'issues.csv').open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['path', 'problem'])
        writer.writeheader()
        writer.writerows(issues)
        
    styles = Counter(row['style'] for row in rows)
    labels = Counter(row['gardiner_label'] for row in rows)
    summary = dict(source='MMM', source_root=str(root), total_crops=len(rows),
                   unique_gardiner_labels=len(labels),
                   source_document_groups=len({(r['style'], r['source_document']) for r in rows}),
                   images_per_style=dict(sorted(styles.items())),
                   duplicate_files=sum(bool(r['duplicate_of']) for r in rows),
                   rejected_files=len(issues),
                   note='Labels are source-provided, not manually verified. No ML train/test split or database import performed.')
    (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build_manifest(args.root, args.output), indent=2))
