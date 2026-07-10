package com.pictoglyph.pictoglyphapi.ingestion.mapping;

public record FieldMatch(
		SourceMappingTarget target,
		String sourceField,
		double confidence,
		String reason
) {
}
