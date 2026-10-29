#!/usr/bin/env python3
"""Review source-provided Cretan inscription image-map regions.

Read the PictoGlyph provisional_crops.csv manifest and render contact sheets of
original photos with annotation boxes alongside the crop. Does NOT assign sign
labels, edit originals, or create a training set. This is an annotation audit.

Requires Pillow. Run from WSL so I: (under /mnt/i) is writable.
"""
from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from datetime import datetime, timezone
import json
from pathlib import Path
import random
import sys

from PIL import Image, ImageDraw, ImageFont, ImageOps, UnidentifiedImageError

DEFAULT_MANIFEST = Path('/mnt/i/PictoGlyph/data/research-datasets/manifests/multiscript-v1/provisional_crops.csv')
DEFAULT_OUTPUT = Path('/mnt/i/PictoGlyph/data/research-datasets/manifests/multiscript-v1/crop-review')
REVIEW_FIELDS = ['sample_id', 'source_id', 'inscription_id', 'image_path', 'region_index',
    'x', 'y', 'width', 'height', 'image_width', 'image_height', 'box_status',
    'review_verdict', 'review_notes', 'source_annotation', 'region_status']
THUMB_W, THUMB_H, TILE_W, TILE_H = 230, 205, 415, 282
CROP_W = TILE_W - THUMB_W - 18


def sample_records(rows, limit, seed, per_image, per_inscription):
    # Spread examples across inscriptions and photographs instead of taking many
    # boxes from a single unusually well-annotated image.
    by_inscription = defaultdict(lambda: defaultdict(list))
    for r in rows:
        group = (r.get('source_id',''), r.get('inscription_id',''))
        by_inscription[group][r.get('image_path','')].append(r)
    rng = random.Random(seed)
    groups = list(by_inscription)
    rng.shuffle(groups)
    for g in groups:
        for path, items in by_inscription[g].items():
            rng.shuffle(items)
    selected = []
    per_group_count = Counter()
    per_img_count = Counter()
    while len(selected) < limit:
        added = 0
        for g in groups:
            if per_group_count[g] >= per_inscription:
                continue
            choices = list(by_inscription[g])
            rng.shuffle(choices)
            for path in choices:
                if per_img_count[path] >= per_image:
                    continue
                lst = by_inscription[g][path]
                if not lst:
                    continue
                row = lst.pop()
                selected.append(row)
                per_group_count[g] += 1
                per_img_count[path] += 1
                added += 1
                break
            if len(selected) >= limit:
                break
        if not added:
            break
    return selected


def draw_centered(image, target_box, canvas):
    thumbnail = ImageOps.contain(image, (target_box[2],target_box[3]), method=Image.Resampling.LANCZOS)
    x = target_box[0] + (target_box[2] - thumbnail.width)//2
    y = target_box[1] + (target_box[3] - thumbnail.height)//2
    canvas.paste(thumbnail, (x,y))


def make_tile(row, sample_id):
    tile = Image.new('RGB', (TILE_W,TILE_H), '#f3f4f6')
    d = ImageDraw.Draw(tile)
    path = Path(row['image_path'])
    label = f"{sample_id:03d} | {row.get('inscription_id') or 'unknown'} | {path.name[:26]}"
    d.text((8,3), label, fill='#111827')
    result = dict(row)
    result.update(sample_id=sample_id, image_width='', image_height='', box_status='',
                  review_verdict='', review_notes='')
    try:
        x = int(float(row['x'])); y = int(float(row['y']))
        w = int(float(row['width'])); h = int(float(row['height']))
        if x < 0 or y < 0 or w <= 0 or h <= 0:
            raise ValueError('Invalid non-positive box')
    except (ValueError, TypeError, KeyError):
        result['box_status'] = 'INVALID_COORDINATES'
        d.text((9,80),'Invalid coordinates',fill='#b91c1c')
        return tile, result

    try:
        with Image.open(path) as loaded:
            original = loaded.convert('RGB')
        iw, ih = original.size
        result['image_width'], result['image_height'] = iw, ih
        if x+w > iw or y+h > ih:
            result['box_status']='OUTSIDE_IMAGE'
            d.text((9,80),f'Box exceeds {iw}x{ih}',fill='#b91c1c')
            return tile, result
        result['box_status']='BOX_INSIDE_IMAGE'
        marked = original.copy()
        marker = ImageDraw.Draw(marked)
        marker.rectangle((x,y,x+w,y+h), outline='#f43f5e',width=max(2,round(min(iw,ih)/110)))
        crop = original.crop((x,y,x+w,y+h))
        draw_centered(marked,(6,25,THUMB_W,THUMB_H),tile)
        draw_centered(crop,(THUMB_W+15,25,CROP_W,THUMB_H),tile)
        d.text((8,237),f'Image {iw}x{ih} | box ({x},{y},{w},{h})',fill='#374151')
        d.text((8,255),'Original + region box                 Crop (not a verified glyph)',fill='#374151')
    except (FileNotFoundError, PermissionError):
        result['box_status']='MISSING_OR_UNREADABLE_IMAGE'
        d.text((9,80),'Image missing or unreadable',fill='#b91c1c')
    except (UnidentifiedImageError, OSError, ValueError) as e:
        result['box_status']='IMAGE_ERROR'
        d.text((9,80),f'Image error: {str(e)[:45]}',fill='#b91c1c')
    return tile, result


def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST)
    p.add_argument('--output',type=Path,default=DEFAULT_OUTPUT)
    p.add_argument('--limit',type=int,default=80)
    p.add_argument('--seed',type=int,default=17)
    p.add_argument('--per-image',type=int,default=2)
    p.add_argument('--per-inscription',type=int,default=4)
    p.add_argument('--per-page',type=int,default=20)
    args=p.parse_args()
    if min(args.limit,args.per_image,args.per_inscription,args.per_page)<=0:
        p.error('Limits must be positive integers')
    if not args.manifest.is_file():
        p.error(f'Manifest not found: {args.manifest}')
    with args.manifest.open(encoding='utf-8-sig',newline='') as f:
        reader=csv.DictReader(f)
        required={'image_path','inscription_id','region_index','x','y','width','height'}
        if not required.issubset(reader.fieldnames or []):
            p.error(f'Missing columns: {sorted(required-set(reader.fieldnames or []))}')
        rows=list(reader)
    if not rows:
        p.error('The provisional crop manifest is empty')
    sample=sample_records(rows,args.limit,args.seed,args.per_image,args.per_inscription)
    args.output.mkdir(parents=True,exist_ok=True)
    created=[];review=[]; page=[];counts=Counter()
    for i,row in enumerate(sample,1):
        tile,r=make_tile(row,i)
        counts[r['box_status']]+=1
        review.append(r)
        page.append(tile)
        if len(page)==args.per_page or i==len(sample):
            cols=4; rows_n=(len(page)+cols-1)//cols
            sheet=Image.new('RGB',(cols*TILE_W, rows_n*TILE_H),'white')
            for idx,img in enumerate(page):
                sheet.paste(img,((idx%cols)*TILE_W,(idx//cols)*TILE_H))
            path=args.output/f'crop_review_{len(created)+1:02d}.jpg'
            sheet.save(path,quality=88,optimize=True)
            created.append(str(path)); page=[]
            print('Created',path,flush=True)
    with (args.output/'crop_review_labels.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=REVIEW_FIELDS,extrasaction='ignore')
        writer.writeheader();writer.writerows(review)
    summary={'generated_at':datetime.now(timezone.utc).isoformat(), 'manifest':str(args.manifest),
             'proposals_in_manifest':len(rows), 'sampled':len(review),
             'unique_images_sampled':len({r['image_path'] for r in review}),
             'unique_inscriptions_sampled':len({r['inscription_id'] for r in review}),
             'status_counts':dict(counts),'seed':args.seed,'sheets':created,
             'warning':'Region boxes are not confirmed glyphs. Review before making training labels.'}
    (args.output/'crop_review_summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k!='sheets'},indent=2))
    print('Review CSV:',args.output/'crop_review_labels.csv')
    return 0

if __name__=='__main__':
    sys.exit(main())
