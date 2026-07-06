package com.pictoglyph.pictoglyphapi.ingestion.api;

public record ManualProcessingFileResponse(
		String originalPath,
		String manualProcessingPath,
		String reason
) {
}
