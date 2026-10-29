#!/usr/bin/env python3
"""PictoGlyph: safe SigLIP2 first-pass triage of extracted research images.

Classifies image *relevance* only; it never deletes, moves, crops or interprets
images, and it does not infer sign identities, archaeological dates or licenses.

Designed for the existing `pictoglyph_ml` Docker container. The container has
read-only access to research-datasets, so write CSV / sample sheet to `/tmp`
and use `docker cp` to copy the results back to the host afterwards.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

RASTER_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp', '.tif', '.tiff', '.bmp', '.gif'}
MODEL_ID = 'google/siglip2-base-patch16-naflex'
MAX_PIXELS = 60_000_000

# Descriptions, not predicted sign identities. The highest prompt in a group
# helps us select image candidates, but neither its score nor ranking is a label.
PROMPTS = {
    'isolated_sign': [
        'A close up image of a single ancient written symbol on a plain background.',
        'A drawing of one ancient hieroglyphic sign or pictographic character.',
    ],
    'inscription': [
        'A photograph of an ancient seal with writing or symbols engraved on it.',
        'A close up photograph of an ancient inscription carved into stone or clay.',
        'A photograph of an old manuscript or tablet covered with ancient writing.',
    ],
    'facsimile': [
        'A black and white tracing of archaeological writing on an ancient artefact.',
        'A drawing of an inscription with several historical writing symbols.',
    ],
    'web_asset': [
        'A screenshot of a modern website interface with menus and buttons.',
        'A decorative website logo or a modern computer interface icon.',
        'A modern advertisement, logo or poster.',
    ],
    'non_inscription': [
        'A landscape photograph with no writing or symbols.',
        'A photograph of a person or building without any inscription.',
        'A photograph of an ancient statue without any visible writing.',
    ],
}
POSITIVE_GROUPS = {'isolated_sign', 'inscription', 'facsimile'}
NEGATIVE_GROUPS = set(PROMPTS) - POSITIVE_GROUPS


def files_for_source(root: Path, source_id: str, limit: int, seed: int) -> list[Path]:
    directory = root / 'scripts' / source_id / 'extracted'
    if not directory.is_dir():
        raise FileNotFoundError(f'Extracted source missing: {directory}')
    images = sorted(p for p in directory.rglob('*')
                    if p.is_file() and p.suffix.lower() in RASTER_SUFFIXES)
    # Reproducible random sampling is more representative than alphabetic first-N.
    if limit > 0 and len(images) > limit:
        source_seed = int(hashlib.sha256(f'{seed}:{source_id}'.encode()).hexdigest()[:16], 16)
        images = sorted(random.Random(source_seed).sample(images, limit))
    return images


def model_and_prompts():
    import torch
    from transformers import AutoProcessor, Siglip2Model
    if not torch.cuda.is_available():
        raise RuntimeError('CUDA GPU unavailable: use the pictoglyph_ml container.')
    print(f'Loading {MODEL_ID} on {torch.cuda.get_device_name(0)}...', flush=True)
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    model = Siglip2Model.from_pretrained(MODEL_ID, torch_dtype=torch.float16).to('cuda').eval()
    groups = [(group, prompt) for group, items in PROMPTS.items() for prompt in items]
    return model, processor, groups


def scores_for_image(image, model, processor, groups):
    import torch
    texts = [item[1] for item in groups]
    # Full forward call performs the learned image/text projections and similarity.
    inputs = processor(text=texts, images=image, padding='max_length',
                       truncation=True, max_length=64, return_tensors='pt').to('cuda')
    with torch.inference_mode():
        logits = model(**inputs).logits_per_image[0]
        values = torch.sigmoid(logits).float().cpu().tolist()
    score_by_group = {key: max(v for (group, _), v in zip(groups, values) if group == key)
                      for key in PROMPTS}
    winner_index = max(range(len(values)), key=lambda i: values[i])
    return score_by_group, groups[winner_index][1]


def triage(scores: dict[str, float], review_margin: float, small: bool) -> tuple[str, float, str]:
    positive = max(scores[g] for g in POSITIVE_GROUPS)
    negative = max(scores[g] for g in NEGATIVE_GROUPS)
    margin = positive - negative
    best_positive = max(POSITIVE_GROUPS, key=lambda g: scores[g])
    if small:
        return 'REVIEW', margin, 'too_small_for_reliable_visual_triage'
    if margin >= review_margin:
        return 'CANDIDATE', margin, best_positive
    if margin <= -review_margin:
        return 'LOW_PRIORITY', margin, 'negative_prompt_preferred'
    return 'REVIEW', margin, 'ambiguous_image_text_match'


def make_contact_sheet(rows: list[dict], path: Path, count: int = 48):
    from PIL import Image, ImageDraw, ImageOps
    if not rows:
        return
    # Show a mixture of decisions, with unreviewed/low-priority prioritised.
    ordered = sorted(rows, key=lambda r: (r['triage'] != 'LOW_PRIORITY',
                                         r['triage'] != 'REVIEW', r['source_id'], r['image_path']))
    selected = ordered[:count]
    cell_w, cell_h, image_h, cols = 240, 215, 164, 4
    n_rows = (len(selected) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * cell_w, n_rows * cell_h), 'white')
    draw = ImageDraw.Draw(sheet)
    for idx, item in enumerate(selected):
        x, y = (idx % cols) * cell_w, (idx // cols) * cell_h
        try:
            with Image.open(item['image_path']) as original:
                thumb = ImageOps.contain(original.convert('RGB'), (cell_w - 12, image_h))
            sheet.paste(thumb, (x + (cell_w - thumb.width) // 2, y))
        except Exception:
            pass
        draw.text((x + 5, y + image_h + 2), f"{item['source_id']} | {item['triage']}", fill='black')
        draw.text((x + 5, y + image_h + 18), Path(item['image_path']).name[:34], fill='black')
        draw.text((x + 5, y + image_h + 34), f"gap: {item['margin']}", fill='black')
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path, quality=82)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('/mnt/i/PictoGlyph/data/research-datasets'))
    parser.add_argument('--source', action='append', required=True,
                        help='Acquisition source id. Repeat this argument for more sources.')
    parser.add_argument('--limit', type=int, default=12,
                        help='Reproducible random sample size PER source (0 = all; default 12).')
    parser.add_argument('--seed', type=int, default=17)
    parser.add_argument('--margin', type=float, default=0.06,
                        help='Uncalibrated, provisional sigmoid-score gap for triage (default 0.06).')
    parser.add_argument('--output', type=Path, default=Path('/tmp/pictoglyph_image_triage.csv'))
    parser.add_argument('--contact-sheet', type=Path, default=Path('/tmp/pictoglyph_image_triage_review.jpg'))
    parser.add_argument('--inventory-only', action='store_true',
                        help='Test discovery, image checks and output without GPU or SigLIP2.')
    args = parser.parse_args()
    if args.limit < 0 or not 0 <= args.margin <= 1:
        parser.error('--limit must be non-negative and --margin between 0 and 1')
    if not args.output.name.lower().endswith('.csv'):
        parser.error('--output must be a .csv file')

    selected = []
    for source_id in dict.fromkeys(args.source):
        found = files_for_source(args.root, source_id, args.limit, args.seed)
        print(f'{source_id}: selected {len(found)} images', flush=True)
        selected += [(source_id, p) for p in found]
    if not selected:
        raise RuntimeError('No raster images found in these source folders')

    model, processor, groups = (None, None, None) if args.inventory_only else model_and_prompts()
    from PIL import Image, ImageOps, UnidentifiedImageError
    rows = []
    seen_hashes: dict[str, str] = {}
    for idx, (source_id, path) in enumerate(selected, start=1):
        row = dict(source_id=source_id, image_path=str(path),
                   relative_path=path.relative_to(args.root / 'scripts' / source_id / 'extracted').as_posix(),
                   width='', height='', triage='REVIEW', explanation='',
                   margin='', positive_score='', negative_score='',
                   isolated_sign='', inscription='', facsimile='', web_asset='', non_inscription='',
                   best_prompt='', sha256='', duplicate_of='', error='')
        try:
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            checksum = digest.hexdigest()
            row['sha256'] = checksum
            if checksum in seen_hashes:
                row.update(triage='DUPLICATE', explanation='byte_identical_image',
                           duplicate_of=seen_hashes[checksum])
                rows.append(row)
                continue
            seen_hashes[checksum] = str(path)
            with Image.open(path) as raw:
                width, height = raw.size
                row.update(width=width, height=height)
                if width * height > MAX_PIXELS:
                    raise ValueError('oversized_image_review_without_loading')
                img = ImageOps.exif_transpose(raw).convert('RGB')
            if args.inventory_only:
                row['explanation'] = 'inventory_only_no_ml_model'
            else:
                scores, prompt = scores_for_image(img, model, processor, groups)
                small = min(width, height) < 64
                decision, margin, explanation = triage(scores, args.margin, small)
                row.update(triage=decision, explanation=explanation, margin=f'{margin:+.4f}',
                           positive_score=f'{max(scores[g] for g in POSITIVE_GROUPS):.4f}',
                           negative_score=f'{max(scores[g] for g in NEGATIVE_GROUPS):.4f}',
                           best_prompt=prompt)
                row.update({key: f'{value:.4f}' for key, value in scores.items()})
        except Exception as exc:
            row['error'] = f'{type(exc).__name__}: {str(exc)[:200]}'
            row['explanation'] = 'image_error_manual_review'
        rows.append(row)
        if idx % 10 == 0 or idx == len(selected):
            counts = Counter(r['triage'] for r in rows)
            print(f'{idx}/{len(selected)} {dict(counts)}', flush=True)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('w', newline='', encoding='utf-8') as dest:
        writer = csv.DictWriter(dest, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    if args.contact_sheet:
        make_contact_sheet(rows, args.contact_sheet)
    summary = dict(generated_at=datetime.now(timezone.utc).isoformat(), model=None if args.inventory_only else MODEL_ID,
                   sources=list(dict.fromkeys(args.source)), seed=args.seed,
                   limit_per_source=args.limit, decision_margin=args.margin,
                   processed=len(rows), counts=dict(Counter(r['triage'] for r in rows)),
                   errors=sum(bool(r['error']) for r in rows),
                   note='Uncalibrated visual triage, not ground truth or authority to delete files')
    summary_path = args.output.with_suffix('.summary.json')
    summary_path.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2), flush=True)
    print('CSV:', args.output, '\nReview sheet:', args.contact_sheet, '\nSummary:', summary_path)


if __name__ == '__main__':
    main()
