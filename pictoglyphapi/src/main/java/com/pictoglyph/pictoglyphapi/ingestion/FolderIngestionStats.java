package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.ingestion.api.ManualProcessingFileResponse;

import java.util.List;

public record FolderIngestionStats(
		List<Long> createdSymbolIds,
		int skippedCount,
		List<ManualProcessingFileResponse> manualProcessingFiles
) {
}