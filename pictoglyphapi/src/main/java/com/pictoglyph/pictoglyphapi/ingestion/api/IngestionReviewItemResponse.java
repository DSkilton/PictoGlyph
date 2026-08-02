package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;

import java.time.LocalDateTime;

public record IngestionReviewItemResponse(
		Long id,
		Long ingetsionJobId,
		int itemIndex,
		String reason,
		JsonNode rawItem,
		IngestionReviewStatus status,
		String resolutionNotes,
		LocalDateTime createdAt,
		LocalDateTime resolvedAt
) {
}
