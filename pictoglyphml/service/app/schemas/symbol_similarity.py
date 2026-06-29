from pydantic import BaseModel, ConfigDict, Field


class SymbolSimilarityMatch(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    symbol_id: int = Field(alias="symbolId")
    similarity: float


class SymbolSimilarityResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query_symbol_id: int = Field(alias="querySymbolId")
    model: str
    matches: list[SymbolSimilarityMatch]