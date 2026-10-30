#!/usr/bin/env python3
"""PictoGlyph research manifest builder.

Read extracted Indus, Cretan Hieroglyphic and Linear A collections without
changing inputs. All image/crop links are source-derived, not semantic labels.
Python standard library only. Outputs CSV/JSON, never modifies the database.
"""
from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys

DEFAULT_ROOT = Path('/mnt/i/PictoGlyph/data/research-datasets')
IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.webp', '.tif', '.tiff', '.bmp'}
MANIFEST_FIELDS = [
    'source_id','script','image_path','image_relpath','image_filename',
    'image_kind','inscription_id','link_status','link_basis','site','region',
    'period','material','object_type','transcription','transcription_status',
    'image_rights','crop_region_count','triage','triage_margin','split_group',
    'source_record_reference'
]
RECORD_FIELDS = [
    'source_id','script','inscription_id','site','region','period','material',
    'object_type','transcription','transcription_status','image_count',
    'image_rights','source_record_reference','note'
]
CROP_FIELDS = [
    'source_id','script','inscription_id','image_path','region_index',
    'x','y','width','height','region_status','label','source_annotation'
]


def write_csv(path: Path, fields: list[str], records: list[dict]) -> None:
    with path.open('w', encoding='utf-8', newline='') as out:
        writer = csv.DictWriter(out, fields, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(records)


def read_json(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def locate(source_dir: Path, filename: str) -> Path | None:
    if not source_dir.exists():
        return None
    found = list(source_dir.glob(f'extracted/**/{filename}'))
    return min(found, key=lambda f: len(f.parts)) if found else None


def js_array(path: Path, marker: str):
    """Parse JS files whose data is a JSON-compatible array with trailing commas.

    Does NOT execute untrusted JavaScript. Fails rather than guess on other syntax.
    """
    source = path.read_text(encoding='utf-8-sig')
    pos = source.find(marker)
    if pos == -1:
        raise ValueError(f'Marker {marker!r} not found in {path}')
    start = source.find('[', pos + len(marker))
    if start == -1:
        raise ValueError(f'Array start not found in {path}')
    # JSON list and embedded objects, not arbitrary JavaScript execution.
    source = re.sub(r',(?=\s*[\]}])', '', source[start:])
    value, _ = json.JSONDecoder().raw_decode(source)
    if not isinstance(value, list):
        raise ValueError(f'Expected array in {path}')
    return value


def triage_lookup(csv_path: Path | None) -> dict[str, dict]:
    if not csv_path:
        return {}
    result = {}
    with csv_path.open(encoding='utf-8-sig', newline='') as inp:
        for row in csv.DictReader(inp):
            p = row.get('image_path', '')
            if p:
                result[p] = row
    return result


def image_row(source: str, script: str, path: Path, root: Path, kind: str, triage: dict) -> dict:
    tri = triage.get(str(path), {})
    return {
        'source_id': source, 'script': script, 'image_path': str(path),
        'image_relpath': str(path.relative_to(root)),
        'image_filename': path.name, 'image_kind': kind,
        'inscription_id': '', 'link_status': 'UNLINKED', 'link_basis': '',
        'site': '', 'region': '', 'period': '', 'material': '', 'object_type': '',
        'transcription': '', 'transcription_status': 'UNKNOWN',
        'image_rights': 'REVIEW_SOURCE_LICENCE', 'crop_region_count': 0,
        'triage': tri.get('triage',''), 'triage_margin': tri.get('margin',''),
        'split_group': '', 'source_record_reference': ''
    }


def choose_images(folder: Path, maximum: int) -> list[Path]:
    paths = sorted((p for p in folder.rglob('*') if p.is_file() and p.suffix.lower() in IMAGE_EXT))
    # Entire source image inventory is the default; a limit is reproducible and alphabetical.
    return paths[:maximum] if maximum else paths


def indus(source_dir: Path, max_images: int, triage: dict):
    marker = locate(source_dir, 'seal_id_and_image_mapping.json')
    if marker is None or marker.parent.name != 'data':
        raise FileNotFoundError(f'Indus seal mapping not found under {source_dir}/extracted')
    repo = marker.parents[3]
    img_dir = repo/'public'/'seal_images'
    inscription_file = marker.parent/'inscriptions.csv'
    mapping = read_json(marker)
    with inscription_file.open(encoding='utf-8-sig', newline='') as inp:
        all_inscriptions = list(csv.DictReader(inp))
    ins_by_id = defaultdict(list)
    for row in all_inscriptions:
        ins_by_id[row['cisi'].strip().casefold()].append(row)
    file_to_ids = defaultdict(list)
    for ins_id, filenames in mapping.items():
        for fn in filenames:
            file_to_ids[Path(fn).name.casefold()].append(ins_id)
    images = []
    image_count_for_id = Counter()
    for p in choose_images(img_dir, max_images):
        row = image_row('indus-lipi', 'Indus', p, repo, 'seal_image', triage)
        ids = sorted(set(file_to_ids.get(p.name.casefold(), [])))
        if ids:
            ins_id = ids[0]
            row['inscription_id'] = ins_id
            row['link_basis'] = 'explicit_seal_image_mapping'
            row['link_status'] = 'LINKED' if len(ids) == 1 else 'AMBIGUOUS'
            m = ins_by_id.get(ins_id.casefold(), [])
            if len(m) == 1 and len(ids) == 1:
                rec=m[0]
                row.update(site=rec.get('site',''),region=rec.get('region',''),
                    period=rec.get('period',''),material=rec.get('material',''),
                    object_type=rec.get('type',''),transcription=rec.get('text',''),
                    transcription_status='SOURCE_TRANSCRIPTION_UNVERIFIED')
            elif not m:
                row['link_status']='MAPPING_ONLY'
            else:
                row['link_status']='AMBIGUOUS'
            row['split_group'] = 'indus:'+ins_id if len(ids)==1 else 'indus:ambiguous:'+p.stem
            row['source_record_reference']=f'src/assets/data/inscriptions.csv:cisi={ins_id}'
            image_count_for_id[ins_id.casefold()]+=1
        if not row['split_group']:
            row['split_group']='indus:unlinked:'+p.stem
        images.append(row)
    records = []
    for r in all_inscriptions:
        ins_id = r.get('cisi','')
        records.append(dict(source_id='indus-lipi',script='Indus',inscription_id=ins_id,
            site=r.get('site',''),region=r.get('region',''),period=r.get('period',''),
            material=r.get('material',''),object_type=r.get('type',''),
            transcription=r.get('text',''),transcription_status='SOURCE_TRANSCRIPTION_UNVERIFIED',
            image_count=image_count_for_id[ins_id.casefold()],image_rights='REVIEW_SOURCE_LICENCE',
            source_record_reference=f'src/assets/data/inscriptions.csv:id={r.get("id","")}',
            note='Do not treat proposed Sanskrit/meaning as decipherment ground truth'))
    return images, records, []


def cretan(source_dir: Path, max_images: int, max_crops: int, triage: dict):
    marker = locate(source_dir, 'CretanHieroInscriptions.js')
    if marker is None:
        raise FileNotFoundError(f'Cretan inscriptions JS not found in {source_dir}/extracted')
    repo = marker.parent
    coordinates_file = repo/'imagemaps.js'
    pairs = js_array(marker, 'var inscriptions = new Map(')
    inscriptions = {str(k):v for k,v in pairs if isinstance(v,dict)}
    coords_data = js_array(coordinates_file, 'var inscriptionCoordinates = ')
    coords_by_img = {v.get('img'):v.get('areas',[]) for v in coords_data if isinstance(v,dict) and v.get('img')}
    by_filename = defaultdict(list)
    for ins_id,r in inscriptions.items():
        for fn in r.get('images',[])+r.get('facsimileImages',[]):
            by_filename[fn.replace('\\','/')].append(ins_id)
    images = []
    crops = []
    image_count_for_id = Counter()
    base_dirs=[repo/'images',repo/'commentary'/'images']
    all_paths=[]
    for folder in base_dirs:
        if folder.exists():all_paths+=choose_images(folder,0)
    all_paths.sort()
    if max_images:all_paths=all_paths[:max_images]
    for p in all_paths:
        kind='sign_reference' if p.parent.name=='images' and p.parent.parent.name=='commentary' else (
            'facsimile' if 'Facsimile' in p.name else 'photograph' if 'Photo' in p.name else 'additional_inscription_image')
        row=image_row('cretan-hieroglyphic','Cretan Hieroglyphic',p,repo,kind,triage)
        rel=p.relative_to(repo).as_posix()
        ids=sorted(set(by_filename.get(rel, [])))
        if ids:
            ins_id=ids[0];r=inscriptions[ins_id]
            row.update(inscription_id=ins_id,
                link_status='LINKED' if len(ids)==1 else 'AMBIGUOUS',
                link_basis='explicit_inscription_image_reference',
                site=r.get('site',''),material=r.get('support',''),
                transcription=r.get('transcription',''),
                transcription_status='SOURCE_TRANSCRIPTION_UNVERIFIED',
                image_rights=r.get('imageRights','REVIEW_SOURCE_LICENCE'),
                split_group='cretan:'+ins_id,
                source_record_reference=f'CretanHieroInscriptions.js:id={ins_id}')
            image_count_for_id[ins_id]+=1
        elif rel.startswith('images/'):
            # A filename prefix is a weak clue, not a verified match.
            match=re.match(r'^(\d{3})',p.name)
            if match and match.group(1) in inscriptions:
                row['inscription_id']=match.group(1)
                row['link_basis']='filename_prefix_unverified'
                row['link_status']='REVIEW_LINK'
                row['split_group']='cretan:'+match.group(1)
        areas=coords_by_img.get(rel, [])
        row['crop_region_count']=len(areas)
        for i,area in enumerate(areas):
            if max_crops and len(crops)>=max_crops:break
            bbox=area.get('coords',{}) if isinstance(area,dict) else {}
            if not all(k in bbox for k in ('x','y','width','height')):continue
            if not all(isinstance(bbox[k],(int,float)) and bbox[k]>=0 for k in ('x','y')):continue
            if not all(isinstance(bbox[k],(int,float)) and bbox[k]>0 for k in ('width','height')):continue
            crops.append(dict(source_id='cretan-hieroglyphic',script='Cretan Hieroglyphic',
                inscription_id=row['inscription_id'],image_path=str(p),region_index=i,
                x=bbox['x'],y=bbox['y'],width=bbox['width'],height=bbox['height'],
                region_status='PROVISIONAL_NOT_VERIFIED_GLYPH',label='',
                source_annotation=f'imagemaps.js:img={rel}'))
        if not row['split_group']:
            row['split_group']='cretan:unlinked:'+p.stem
        images.append(row)
    records=[]
    for ins_id,r in inscriptions.items():
        records.append(dict(source_id='cretan-hieroglyphic',script='Cretan Hieroglyphic',
            inscription_id=ins_id,site=r.get('site',''),region='',period='',
            material=r.get('support',''),object_type='',transcription=r.get('transcription',''),
            transcription_status='SOURCE_TRANSCRIPTION_UNVERIFIED',
            image_count=image_count_for_id[ins_id],image_rights=r.get('imageRights','REVIEW_SOURCE_LICENCE'),
            source_record_reference=f'CretanHieroInscriptions.js:id={ins_id}',
            note='Image-map bounding boxes are provisional regions, not validated glyph labels'))
    return images,records,crops


def linear_a(source_dir: Path):
    marker=locate(source_dir,'corpus.json')
    if marker is None or marker.parent.name!='data' or marker.parent.parent.name!='linear_a':
        raise FileNotFoundError(f'Linear A corpus not found under {source_dir}/extracted')
    corpus=read_json(marker)
    records=[]
    for k,rec in corpus.items():
        if not isinstance(rec,dict):continue
        records.append(dict(source_id='lineara-corpus',script='Linear A',inscription_id=rec.get('id',k),
            site=rec.get('site',''),region='',period=rec.get('period',''),material='',
            object_type=rec.get('type',''),transcription=rec.get('unicode_text',''),
            transcription_status='SOURCE_TRANSCRIPTION_UNVERIFIED',image_count=0,
            image_rights='NO_IMAGE_IN_SOURCE',source_record_reference=f'linear_a/data/corpus.json:{k}',
            note='Metadata only; source contains no matching inscription images'))
    return [],records,[]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=DEFAULT_ROOT,help='Research datasets root on I:')
    parser.add_argument('--output',type=Path,default=None,help='New report directory; default root/manifests')
    parser.add_argument('--sources',nargs='+',choices=['indus-lipi','cretan-hieroglyphic','lineara-corpus'],
        default=['indus-lipi','cretan-hieroglyphic','lineara-corpus'])
    parser.add_argument('--max-images-per-source',type=int,default=0,help='0 means all images')
    parser.add_argument('--max-crop-proposals',type=int,default=0,help='0 means all proposals')
    parser.add_argument('--triage-csv',type=Path,default=None,help='Optional existing SigLIP2 triage CSV')
    args=parser.parse_args()
    if args.max_images_per_source<0 or args.max_crop_proposals<0:parser.error('Limits cannot be negative')
    root=args.root.resolve()
    out=(args.output or root/'manifests'/'multiscript-v1').resolve()
    for source in args.sources:
        if not (root/'scripts'/source/'extracted').is_dir():
            parser.error(f'Not extracted: {root}/scripts/{source}/extracted')
    lookup=triage_lookup(args.triage_csv)
    imgs=[];records=[];crops=[];summaries={}
    for source in args.sources:
        source_dir=root/'scripts'/source
        print(f'Reading {source}...',flush=True)
        if source=='indus-lipi': i,r,c=indus(source_dir,args.max_images_per_source,lookup)
        elif source=='cretan-hieroglyphic': i,r,c=cretan(source_dir,args.max_images_per_source,args.max_crop_proposals,lookup)
        else:i,r,c=linear_a(source_dir)
        imgs+=i;records+=r;crops+=c
        s={'image_rows':len(i),'inscription_records':len(r),'provisional_crop_regions':len(c),
           'image_link_status':dict(Counter(x['link_status'] for x in i)),
           'image_kinds':dict(Counter(x['image_kind'] for x in i))}
        summaries[source]=s
        print(f'  {len(i)} image records, {len(r)} inscription records, {len(c)} provisional crop boxes; links: {s["image_link_status"]}',flush=True)
    out.mkdir(parents=True,exist_ok=True)
    write_csv(out/'images.csv',MANIFEST_FIELDS,imgs)
    write_csv(out/'inscriptions.csv',RECORD_FIELDS,records)
    write_csv(out/'provisional_crops.csv',CROP_FIELDS,crops)
    report={'generated_at':datetime.now(timezone.utc).isoformat(),
        'input_root':str(root),'output_dir':str(out), 'sources':summaries,
        'totals':{'images':len(imgs),'inscription_records':len(records),'provisional_crop_regions':len(crops)},
        'warnings':[
            'Not a training set. Image-to-inscription links are not individual glyph labels.',
            'Cretan image-map regions have not been visually confirmed or converted to glyph crops.',
            'Indus proposed readings and meanings are not established decipherment ground truth.',
            'Licences and image reuse rights must be reviewed before publishing or training.',
            'Split evaluation data by inscription/artefact to prevent leakage.',
            'Only explicit source links are LINKED; filename-prefix guesses remain REVIEW_LINK.'
        ]}
    (out/'summary.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print('Outputs:',*(str(out/name) for name in ['images.csv','inscriptions.csv','provisional_crops.csv','summary.json']),sep='\n  ',flush=True)
    return 0

if __name__=='__main__':
    sys.exit(main())
