"""Reproduce SigLIP2 glyph-crop comparisons from siglip2_setup_and_test.ipynb.

Run inside pictoglyph_ml using the repo's read-only /workspace mount.
"""
from pathlib import Path
import sys

import torch
import torch.nn.functional as F
from PIL import Image, ImageOps
from transformers import AutoProcessor, Siglip2Model

ROOT = Path('/workspace/pictoglyph/pictoglyphml')
sys.path.insert(0, str(ROOT / 'service'))
from app.services.image_preprocessing import preprocess_shape

MODEL_ID = 'google/siglip2-base-patch16-naflex'
CROP_SOURCES = {
    'falcon_1': ('Untitled.png', (30, 35, 100, 140)),
    'falcon_2': ('Untitled.png', (90, 20, 155, 130)),
    'papyrus_falcon': ('falcon_papyrus.png', (100, 180, 450, 576)),
    'eye': ('Untitled.png', (170, 35, 255, 120)),
}


def load_crop(filename: str, box: tuple[int, int, int, int]) -> Image.Image:
    path = ROOT / filename
    if not path.is_file():
        raise FileNotFoundError(f'Missing source image: {path}')
    with Image.open(path) as image:
        return image.convert('RGB').crop(box)


def prepare(image: Image.Image, variant: str) -> Image.Image:
    if variant == 'original':
        return image
    if variant == 'grayscale':
        return ImageOps.grayscale(image).convert('RGB')
    inverted_edges = preprocess_shape(image).image
    if variant == 'edges':
        return ImageOps.invert(inverted_edges)
    if variant == 'inverted_edges':
        return inverted_edges
    raise ValueError(variant)


def main() -> None:
    if not torch.cuda.is_available():
        raise RuntimeError('CUDA GPU is required for this experiment')
    device = 'cuda'
    print(f'GPU: {torch.cuda.get_device_name(0)}')
    print(f'Model: {MODEL_ID}')
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    model = Siglip2Model.from_pretrained(MODEL_ID, torch_dtype=torch.float16).to(device).eval()

    names = list(CROP_SOURCES)
    crops = [load_crop(*CROP_SOURCES[name]) for name in names]
    print('Crops:', ', '.join(f'{name}={crop.size}' for name, crop in zip(names, crops)))
    print('\nVariant           Falcon1/Falcon2  Falcon1/Papyrus  Falcon1/Eye  Papyrus−Eye margin')
    print('-' * 90)

    for variant in ('original', 'grayscale', 'edges', 'inverted_edges'):
        images = [prepare(image, variant) for image in crops]
        inputs = {key: value.to(device) for key, value in processor(images=images, return_tensors='pt').items()}
        with torch.inference_mode():
            features = model.get_image_features(**inputs).pooler_output
            features = F.normalize(features.float(), dim=-1)
            similarities = (features @ features.T).cpu()
        f1_f2 = similarities[0, 1].item()
        f1_papyrus = similarities[0, 2].item()
        f1_eye = similarities[0, 3].item()
        print(f'{variant:<19} {f1_f2:>8.4f}        {f1_papyrus:>8.4f}        {f1_eye:>8.4f}        {f1_papyrus - f1_eye:>+8.4f}')

    print('\nPositive Papyrus−Eye margin means Falcon 1 ranks the papyrus falcon above the eye.')
    print('These are exploratory similarities, not validated recognition accuracy.')


if __name__ == '__main__':
    main()
