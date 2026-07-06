package com.pictoglyph.pictoglyphapi.ingestion.api;

public record ApiManualProcessingItemResponse (
	int itemIndex,
	String reason,
	String rawItem
) {
}
