package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import java.util.List;

public record SourceMappingValidationResult(
		boolean valid,
		List<String> errors,
		List<String> warnings
) {

	public SourceMappingValidationResult {
		errors = errors == null ? List.of() : List.copyOf(errors);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}
}
