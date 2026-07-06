package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionStatus;

import java.util.List;

public record ApiIngestionResultResponse(
		Long ingestionJobId,
		String sourceType,
		String sourceName,
		String sourcePath,
		IngestionStatus status,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		List<Long> createdSymbolIds,
		List<ApiManualProcessingItemResponse> manualProcessingItems
) {
}
