package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;

import java.util.List;

public record IngestionResultResponse(
		Long ingestionJobId,
		String sourceType,
		String sourcePath,
		IngestionStatus status,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		String manualProcessingFolder,
		List<Long> createdSymbolIds,
		List<ManualProcessingFileResponse> manualProcessingFiles
) {
}
