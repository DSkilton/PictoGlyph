from PIL import Image, ImageFilter, ImageOps


def preprocess_shape(image: Image.Image) -> Image.Image:
    """
    Convert an image into a shape-focused edge representation.

    The image is converted to grayscale, lightly blurred to reduce noise,
    transformed into an edge map, contrast-adjusted, and inverted so that
    dark outlines appear on a light background.

    Args:
        image: The source PIL image to preprocess.

    Returns:
        A three-channel RGB PIL image containing the processed edge map.
    """
    gray = ImageOps.grayscale(image)

    blurred = gray.filter(ImageFilter.GaussianBlur(radius=1.2))
    edges = blurred.filter(ImageFilter.FIND_EDGES)

    edges = ImageOps.autocontrast(edges)
    edges = ImageOps.invert(edges)

    return edges.convert("RGB")
