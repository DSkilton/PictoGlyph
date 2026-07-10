package com.pictoglyph.pictoglyphapi.ingestion.api;

import jakarta.validation.constraints.NotBlank;

public record SourceMappingProposalRequest(
		@NotBlank String sourceName,
		@NotBlank String apiUrl,
		String itemArrayField
) {
}
