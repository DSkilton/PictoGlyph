package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateIngestionReviewItemRequest(
		@NotNull
		IngestionReviewStatus status,

		@Size(max = 2000)
		String resolutionNotes
) {
}
