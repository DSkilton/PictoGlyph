from app.schemas.symbol_similarity import (
    SymbolSimilarityMatch,
    SymbolSimilarityResponse
)

class SymbolSimilarityService:
    def find_similar_symbols(self, symbol_id: int) -> SymbolSimilarityResponse:
        return SymbolSimilarityResponse(
            querySymbolId=symbol_id,
            model="mock-symbol-similarity-v1",
            mathches=[
                SymbolSimilarityMatch(symbolId=2, similarity=0.87),
                SymbolSimilarityMatch(symbolId=3, similarity=0.81),
                SymbolSimilarityMatch(symbolId=4, similarity=0.74),
            ],
        )
