from fastapi import FastAPI

from app.schemas.symbol_similarity import SymbolSimilarityResponse
from app.services.symbol_similarity_service import SymbolSimilarityService

app = FastAPI(title="PictoGlyph ML Service")

symbol_similarity_service = SymbolSimilarityService()


@app.get("/")
def root() -> dict[str, str]:
    return {
        "service": "PictoGlyph ML Service",
        "status": "running",
    }


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/symbols/{symbol_id}/similar", response_model=SymbolSimilarityResponse)
def find_similar_symbols(symbol_id: int):
    return symbol_similarity_service.find_similar_symbols(symbol_id)

