from datetime import datetime
from enum import Enum
from typing import Any

from pydantic import BaseModel, Field

ML_CONTRACT_VERSION = "1.0"


class MLProcessingTaskingType(str, Enum):
    GENERATE_IMAGE_EMBEDDING = "GENERATE_IMAGE_EMBEDDING"
    FIND_SIMILAR_SYMBOLS = "FIND_SIMILAR_SYMBOLS"
    CLASSIFY_SYMBOLS = "CLASSIFY_SYMBOLS"


class MLProcessingResultStatus(str, Enum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class MlProcessingRequest(BaseModel):
    contractVersion: str
    jobId: int
    symbolId: int
    taskType: MLProcessingTaskingType
    modelProfile: str
    imagePath: str
    inputChecksum: str
    symbolCode: str | None = None
    languageId: int | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class MlModelResult(BaseModel):
    modelName: str
    modelVersion: str
    embeddingDimension: int
    embedding: list[float] = Field(default_factory=list)
    preprocessing: dict[str, Any] = Field(default_factory=dict)


class MlProcessingResponse(BaseModel):
    contractVersion: str
    jobId: int
    symbolId: int
    status: MLProcessingResultStatus
    modelResults: list[MlModelResult] = Field(default_factory=list)
    processedAt: datetime
    errorMessage: str | None = None

