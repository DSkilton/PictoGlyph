import hashlib
import math
from pathlib import Path

from app.services.embedding_models import (
    EmbeddingOutput,
    ImageEmbeddingModel,
)
from app.schemas.ml_processing import MlProcessingRequest


class MockSiglipEmbeddingModel(ImageEmbeddingModel):

    EMBEDDING_DIMENSION = 8

    @property
    def model_name(self) -> str:
        return "siglip2"


    @property
    def model_version(self) -> str:
        return "mock-v1"


    def embed_image(self, image_path: Path, request: MlProcessingRequest) -> EmbeddingOutput:
        seed = (
            f"{request.input_checksum}:"
            f"{request.symbol_id}:"
            f"{self.model_name}:"
        )

        digest = hashlib.sha256(seed.encode("utf-8")).digest()

        raw_values = [(digest[index] - 127.5) / 127.5
                      for index in range(self.EMBEDDING_DIMENSION)]

        magnitude = math.sqrt(sum(value * value
                                  for value in raw_values))

        if magnitude == 0:
            normalized_values = [0.0] * self.EMBEDDING_DIMENSION
        else:
            embedding = [
                round(value / magnitude, 8)
                for value in raw_values
            ]

        return EmbeddingOutput(
            model_name=self.model_name,
            model_version=self.model_version,
            embedding=embedding,
            preprocessing={
                "implemnentation": "deterministic mock",
                "normalization": "l2",
                "imagePathReceived": str(image_path),
                "mock": True,
            },
        )