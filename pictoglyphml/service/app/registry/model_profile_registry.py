from collections.abc import Callable

from app.services.embedding_models import ImageEmbeddingModel
from app.services.mock_embedding_model import (
    MockSiglipEmbeddingModel,
)


ModelFactory = Callable[[], ImageEmbeddingModel]


class ModelProfileRegistry:
    def __init__(self, profile_factories: dict[str, list[ModelFactory]] | None = None) -> None:
        self._profile_factories: dict[str, list[ModelFactory]] = profile_factories if profile_factories is not None else {
            "SIGLIP_BASELINE_V1": [MockSiglipEmbeddingModel,
            ],
        }

        self._model_cache: dict[str, list[ImageEmbeddingModel]] = {}


    def models_for(self, model_profile: str,) -> list[ImageEmbeddingModel]:
        factories = self._profile_factories.get(model_profile)

        if factories is None:
            raise ValueError(f"Unknown model profile: {model_profile}")

        if model_profile not in self._model_cache:
            self._model_cache[model_profile] = [
                factory()
                for factory in factories
            ]

        return list(self._model_cache[model_profile])
