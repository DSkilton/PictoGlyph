package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.ingestion.api.ApiManualProcessingItemResponse;

import java.util.List;

public record ApiIngestionStats(
		List<Long> createdSymbolsIds,
		int skippedCount,
		List<ApiManualProcessingItemResponse> manualProcessingItems
) {
}
