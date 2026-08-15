from datetime import datetime
from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

ML_CONTRACT_VERSION = "1.0"

class MlProcessingTaskType(str, Enum):
    GNERATE_IMAGE_EBEDDING = "GENERATE_IMAGE_EMBEDDING"
    FIND_SIMILAR_SYMBOLS = "FIND_SIMILAR_SYMBOLS"
    CLASSIFY_SYMBOLS = "CLASSIFY_SYMBOLS"
    
    
class MlProcessingTaskStatus(str, Enum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    
    
class MlProcessingRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    contract_version: str = Field(alias="contractVersion")
    job_id: int = Field(alias="jobId")
    symbol_id: int = Field(alias="symbolId")
    task_type: MlProcessingTaskType = Field(alias="taskType")
    model_profile: str = Field(alias="modelProfile")
    image_path: str = Field(alias="imagePath")
    input_checksum: str = Field(alias="inputChecksum")
    symbol_code: str | None = Field(default=None, alias="symbolCode")
    language_id: int | None = Field(default=None, alias="languageId")
    metadata: dict[str, Any] = Field(default_factory=dict)


class MlModelResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    embedding_dimension: int = Field(alias="embeddingDimension")
    embedding: list[float] = Field(default_factory=list)
    preprocessing: dict[str, Any] = Field(default_factory=dict)


class MlProcessingResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    contract_version: str = Field(alias="contractVersion")
    job_id: int = Field(alias="jobId")
    symbol_id: int = Field(alias="symbolId")
    status: MlProcessingTaskStatus
    model_results: list[MlModelResult] = Field(
        default_factory=list,
        alias="modelResults"
    )
    processed_at: datetime = Field(alias="processedAt")
    error_message: str | None = Field(
        default=None, 
        alias="errorMessage"
    )