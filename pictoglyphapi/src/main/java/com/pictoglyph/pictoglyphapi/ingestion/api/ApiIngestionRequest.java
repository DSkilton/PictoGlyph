package com.pictoglyph.pictoglyphapi.ingestion.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApiIngestionRequest(
		@NotNull Long languageId,
		@NotBlank String sourceName,
		@NotBlank String apiUrl,

		@NotNull
		@Valid
		SourceFieldMapping sourceFieldMapping
) {
}
