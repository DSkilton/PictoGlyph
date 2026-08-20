from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.schemas.ml_processing import MlProcessingRequest


@dataclass(frozen=True)
class EmbeddingOutput:
    model_name: str
    model_version: str
    embedding: list[float]
    preprocessing: dict[str, Any]


class ImageEmbeddingModel(ABC):
    @property
    @abstractmethod
    def model_name(self) -> str:
        """Return the name used to identify the model."""

    @property
    @abstractmethod
    def model_version(self) -> str:
        """Define the exact model or weights version."""

    @abstractmethod
    def embed_image(self, image_path: Path, request: MlProcessingRequest) -> EmbeddingOutput:
        """Generate an embedding for the given image."""

