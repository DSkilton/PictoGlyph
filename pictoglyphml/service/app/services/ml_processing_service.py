from datetime import datetime, timezone
from pathlib import Path

from app.schemas.ml_processing import (
    ML_CONTRACT_VERSION,
    MlModelResult,
    MlProcessingRequest,
    MlProcessingResponse,
    MlProcessingResultStatus,
    MlProcessingTaskType,
)
from app.registry.model_profile_registry import (
    ModelProfileRegistry,
)


class MlProcessingService:
    def __init__(self, profile_registry: ModelProfileRegistry) -> None:
        self._profile_registry = profile_registry


    def process(self, request:MlProcessingRequest) -> MlProcessingResponse:
        try:
            self._validate_request(request)
            model_results = []
            models = self._profile_registry.models_for(request.model_profile)

            for model in models:
                output = model.embed_image(
                    Path(request.image_path),
                    request,
                )

                model_results.append(
                    MlModelResult(
                        modelName = output.model_name,
                        modelVersion = output.model_version,
                        embeddingDimension = len(output.embedding),
                        embedding = output.embedding,
                        preprocessing = output.preprocessing,
                    )
                )

            return MlProcessingResponse(
                contractVersion = ML_CONTRACT_VERSION,
                jobId = request.job_id,
                symbolId = request.symbol_id,
                status = (MlProcessingResultStatus.COMPLETED),
                modelResults = model_results,
                processedAt = datetime.now(timezone.utc),
                errorMessage = None,
            )

        except Exception as e:
            return MlProcessingResponse(
                contractVersion = ML_CONTRACT_VERSION,
                jobId = request.job_id,
                symbolId = request.symbol_id,
                status = (MlProcessingResultStatus.FAILED),
                modelResults = [],
                processedAt = datetime.now(timezone.utc),
                errorMessage = str(e),
            )


    def _validate_request(self, request: MlProcessingRequest) -> None:
        if request.contract_version != ML_CONTRACT_VERSION:
            raise ValueError(
                f"Unsupported ML contract version: {request.contract_version}. "
                f"Expected: {ML_CONTRACT_VERSION}"
            )

        if request.task_type != MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING:
            raise ValueError(
                f"Unsupported task type: {request.task_type}. "
                f"Expected: {MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING}"
            )

        if not request.input_checksum.strip():
            raise ValueError("Input checksum is required.")
