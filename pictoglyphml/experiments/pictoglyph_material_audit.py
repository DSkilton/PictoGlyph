import csv
import json
import re
from collections import Counter
from pathlib import Path

root = Path('/mnt/i/PictoGlyph/data/research-datasets/commons-writing/overnight-1500')
report = root / 'material-hints-review.csv'
patterns = {
    'Stone': r'\b(?:stone|limestone|marble|granite|sandstone|slate|basalt)\b',
    'Paper': r'\b(?:paper|paperboard)\b',
    'Papyrus': r'\b(?:papyrus|papyri)\b',
    'Parchment': r'\b(?:parchment|vellum)\b',
    'Clay': r'\b(?:clay|terracotta|earthenware)\b',
    'Ceramic': r'\b(?:ceramic|pottery|porcelain)\b',
    'Metal': r'\b(?:metal|bronze|iron|silver|gold|copper|brass|lead)\b',
    'Wood': r'\b(?:wood|wooden|timber)\b',
    'Plaster': r'\b(?:plaster|stucco)\b',
    'Textile': r'\b(?:textile|cloth|linen|fabric|silk)\b',
    'Glass': r'\bglass\b',
    'Wax': r'\bwax\b',
}
patterns = {name: re.compile(regex, re.I) for name, regex in patterns.items()}
photos = {}

def metadata_value(metadata, key):
    value = metadata.get(key, {})
    return str(value.get('value', '')) if isinstance(value, dict) else ''

for plan_path in sorted(root.glob('*/*/commons-writing-plan.json')):
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    for item in plan['candidates']:
        if item.get('status') != 'PREVIEW_READY':
            continue
        pageid = item['pageid']
        image = plan_path.parent / 'images' / f'{pageid}.jpg'
        sidecar = image.with_suffix('.json')
        if not image.is_file() or not sidecar.is_file():
            continue
        saved = json.loads(sidecar.read_text(encoding='utf-8'))
        digest = saved.get('sha256')
        if not digest:
            continue
        record = photos.setdefault(digest, {'ids': set(), 'title': item.get('title', ''), 'scripts': set(), 'contexts': set(), 'clues': set(), 'license': item.get('license', '')})
        record['ids'].add(pageid)
        record['scripts'].add(plan['scriptQuery'])
        record['contexts'].add(plan['mediumQuery'])
        metadata = item.get('sourceMetadata', {})
        fields = {'title': item.get('title', ''), 'description': metadata_value(metadata, 'ImageDescription'), 'objectName': metadata_value(metadata, 'ObjectName'), 'categories': metadata_value(metadata, 'Categories')}
        for material, pattern in patterns.items():
            for field, content in fields.items():
                match = pattern.search(content)
                if match:
                    record['clues'].add((material, field, match.group(0).lower()))

counts = Counter()
with report.open('w', newline='', encoding='utf-8') as handle:
    writer = csv.writer(handle)
    writer.writerow(['commonsPageIds', 'title', 'materialHints', 'matchedEvidence', 'scriptSearchHints', 'contextSearchHints', 'licence', 'sha256', 'materialStatus'])
    for digest, record in sorted(photos.items(), key=lambda entry: min(entry[1]['ids'])):
        materials = sorted({clue[0] for clue in record['clues']})
        for material in materials or ['Unknown']:
            counts[material] += 1
        writer.writerow([','.join(map(str, sorted(record['ids']))), record['title'], ', '.join(materials) or 'Unknown', '; '.join(f'{material} ({field}: {word})' for material, field, word in sorted(record['clues'])), ', '.join(sorted(record['scripts'])), ', '.join(sorted(record['contexts'])), record['license'], digest, 'SOURCE_TEXT_HINT_NOT_VERIFIED' if materials else 'UNKNOWN'])

print(f'Unique stored images: {len(photos)}')
print('Material keyword hints (a photograph may appear in multiple categories):')
for material, count in counts.most_common():
    print(f'  {material:12} {count:4}')
print('\nExamples found through multiple search contexts:')
multiple = [r for r in photos.values() if len(r['contexts']) > 1]
for record in sorted(multiple, key=lambda r: (-len(r['contexts']), min(r['ids'])))[:8]:
    print(f"  {min(record['ids'])}: {', '.join(sorted(record['contexts']))} | {record['title'][:75]}")
print(f'\nReviewable CSV: {report}')
