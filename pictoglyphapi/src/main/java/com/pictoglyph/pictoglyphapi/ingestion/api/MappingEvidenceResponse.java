package com.pictoglyph.pictoglyphapi.ingestion.api;

public record MappingEvidenceResponse(
		String targetField,
		String sourceField,
		double confidence,
		String reason
) {
}
