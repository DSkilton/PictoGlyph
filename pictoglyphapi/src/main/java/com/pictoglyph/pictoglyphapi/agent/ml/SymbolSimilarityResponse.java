package com.pictoglyph.pictoglyphapi.agent.ml;

import java.util.List;

public record SymbolSimilarityResponse(
		Long querySymbolId,
		String model,
		List<SymbolSimilarityMatch> matches
) {
}
