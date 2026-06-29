package com.pictoglyph.pictoglyphapi.agent.ml;

public interface SymbolSimilarityClient {

	SymbolSimilarityResponse findSimilarSymbols(Long symbolId);
}
