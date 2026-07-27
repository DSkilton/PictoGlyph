package com.pictoglyph.pictoglyphapi.ingestion.api;

import java.util.List;

public record ApiSourceProfileValidationResponse(
		ApiSourceProfileResponse profile,
		List<String> warnings
) {

	public ApiSourceProfileValidationResponse {
		warnings = warnings == null
				? List.of()
				: List.copyOf(warnings);
	}
}
