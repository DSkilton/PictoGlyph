package com.pictoglyph.pictoglyphapi.ingestion.api;

import java.util.List;

public record IngestionResultResponse(
		String sourceType,
		String sourcePath,
		int importedCount,
		int skippedCount,
		List<Long> createdSymbolIds
) {
}
