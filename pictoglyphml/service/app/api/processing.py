from fastapi import APIRouter

from app.registry.model_profile_registry import (
    ModelProfileRegistry
)

from app.schemas.ml_processing import (
    MlProcessingRequest,
    MlProcessingResponse,
)

from app.services.ml_processing_service import (
    MlProcessingService,
)

router = APIRouter(
    prefix="/v1",
    tags=["ml-processing"],
)

processing_service = MlProcessingService(
    ModelProfileRegistry()
)

@router.post(
    "/process", 
    response_model=MlProcessingResponse,
    response_model_by_alias=True,
)

def process_image(request: MlProcessingRequest) -> MlProcessingResponse:
    return processing_service.process(request)
