from app.services.embedding_models import ImageEmbeddingModel
from app.services.mock_embedding_model import (
    MockSiglipEmbeddingModel,
)


class ModelProfileRegistry:
    def __init__(self) -> None:
        self._profiles: dict[str, list[ImageEmbeddingModel],] = {
            "SIGLIP_BASELINE_V1": [MockSiglipEmbeddingModel(),
            ],
        }


    def models_for(self, model_profile: str,) -> list[ImageEmbeddingModel]:
        models = self._profiles.get(model_profile)

        if models is None:
            raise ValueError(f"Unknown model profile: {model_profile}")

        return list(models)
