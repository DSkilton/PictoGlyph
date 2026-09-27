from dataclasses import dataclass
from typing import Any

from PIL import Image, ImageFilter, ImageOps

GAUSSIAN_BLUR_RADIUS = 1.2
EDGE_DETECTION_NAME = "FIND_EDGES"

@dataclass(frozen=True)
class ImagePreprocessingOutput:
    image: Image.Image
    metadata: dict[str, Any]


def preprocess_shape(image: Image.Image) -> ImagePreprocessingOutput:
    """
    Convert an image into a shape-focused edge representation.

    The image is converted to grayscale, lightly blurred to reduce noise,
    transformed into an edge map, contrast-adjusted, and inverted so that
    dark outlines appear on a light background.

    Args:
        image: The source PIL image to preprocess.

    Returns:
        The processed RGB image and metadata describing the preprocessing.
    """
    gray = ImageOps.grayscale(image)

    blurred = gray.filter(ImageFilter.GaussianBlur(radius=GAUSSIAN_BLUR_RADIUS))
    edges = blurred.filter(ImageFilter.FIND_EDGES)

    edges = ImageOps.autocontrast(edges)
    edges = ImageOps.invert(edges)

    processed_image = edges.convert("RGB")

    return ImagePreprocessingOutput(
        image=processed_image,
        metadata={
            "shapePreprocessing": True,
            "grayscale": True,
            "gaussianBlurRadius": GAUSSIAN_BLUR_RADIUS,
            "edgeDetection": EDGE_DETECTION_NAME,
            "autocontrast": True,
            "inverted": True,
        },
    )
