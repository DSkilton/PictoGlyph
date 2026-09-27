import logging

from pathlib import Path

import torch
from PIL import Image
from transformers import AutoProcessor, Siglip2Model

from app.constants import L2_NORMALISATION

from app.schemas.ml_processing import MlProcessingRequest
from app.services.embedding_models import (
    EmbeddingOutput,
    ImageEmbeddingModel,
)

from app.services.image_preprocessing import preprocess_shape


logger = logging.getLogger(__name__)


class Siglip2EmbeddingModel(ImageEmbeddingModel):
    MODEL_ID = "google/siglip2-base-patch16-naflex"


    def __init__(self) -> None:
        if not torch.cuda.is_available():
            raise RuntimeError("SigLIP2 requires CUDA, but no CUDA capable GPU was detected")
        
        self._device = torch.device("cuda")

        logger.info(
            "Loading SigLIP2 model '%s' on %s (%s)",
            self.MODEL_ID,
            self._device,
            torch.cuda.get_device_name(0),
        )

        self._processor = AutoProcessor.from_pretrained( 
            self.MODEL_ID
        )

        self._model = Siglip2Model.from_pretrained(
            self.MODEL_ID,
            torch_dtype=torch.float16,
        ).to(self._device)

        self._model.eval()


    @property
    def model_name(self) -> str:
        return "siglip2"


    @property
    def model_version(self) -> str:
        return self.MODEL_ID


    def embed_image(self, image_path: Path, request: MlProcessingRequest,) -> EmbeddingOutput:
        with Image.open(image_path) as source_image: 
            image = source_image.convert("RGB")

        preprocessed = preprocess_shape(image)

        inputs = self._processor(
            images=preprocessed.image,
            return_tensors="pt",
        )

        inputs = {
            name: value.to(self._device)
            for name, value in inputs.items()
        }

        with torch.inference_mode():
            output = self._model.get_image_features(**inputs)

        embedding = output.pooler_output
        embedding = torch.nn.functional.normalize(
            embedding,
            dim=-1,
        )

        embedding_values = (embedding
            .squeeze(0)
            .float()
            .cpu()
            .tolist()
        )

        return EmbeddingOutput(
            model_name=self.model_name,
            model_version=self.model_version,
            embedding=embedding_values,
            preprocessing={
                **preprocessed.metadata,
                "normalization": L2_NORMALISATION,
            },
        )
