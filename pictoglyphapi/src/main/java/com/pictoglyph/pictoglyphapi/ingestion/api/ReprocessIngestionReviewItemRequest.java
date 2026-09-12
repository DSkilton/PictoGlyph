package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReprocessIngestionReviewItemRequest(
		@NotNull
		Long languageId,

		@NotBlank
		String sourceName,

		@NotNull
		@Valid
		SourceFieldMapping sourceFieldMapping,

		@NotNull
		JsonNode correctedItem,

		@Size(max = 2000)
		String resolutionNotes
) {
}
