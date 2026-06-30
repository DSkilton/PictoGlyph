from app.schemas.symbol_similarity import (
    SymbolSimilarityMatch,
    SymbolSimilarityResponse,
)


MOCK_MODEL_NAME = "mock-symbol-similarity-v1"

MOCK_SIMILARITY_MATCHES = [
    (2, 0.87),
    (3, 0.81),
    (4, 0.74),
]


class SymbolSimilarityService:
    def find_similar_symbols(self, symbol_id: int) -> SymbolSimilarityResponse:
        matches = [
            SymbolSimilarityMatch(symbol_id=match_symbol_id, similarity=similarity)
            for match_symbol_id, similarity in MOCK_SIMILARITY_MATCHES
        ]

        return SymbolSimilarityResponse(
            query_symbol_id=symbol_id,
            model=MOCK_MODEL_NAME,
            matches=matches,
        )
