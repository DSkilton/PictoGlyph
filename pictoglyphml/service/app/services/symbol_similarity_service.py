from app.schemas.symbol_similarity import (
    SymbolSimilarityMatch,
    SymbolSimilarityResponse
)

class SymbolSimilarityService:
    def find_similar_symbols(self, symbol_id: int) -> SymbolSimilarityResponse:
        return SymbolSimilarityResponse(
            query_symbol_id=symbol_id,
            model="mock-symbol-similarity-v1",
            matches=[
                SymbolSimilarityMatch(symbol_id=2, similarity=0.87),
                SymbolSimilarityMatch(symbol_id=3, similarity=0.81),
                SymbolSimilarityMatch(symbol_id=4, similarity=0.74),
            ],
        )
