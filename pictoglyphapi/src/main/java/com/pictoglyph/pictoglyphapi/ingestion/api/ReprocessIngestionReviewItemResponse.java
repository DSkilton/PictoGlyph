package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;

import java.time.LocalDateTime;

public record ReprocessIngestionReviewItemResponse(
	Long reviewItemId,
	Long ingestionJobId,
	Long symbolId,
	IngestionReviewStatus status,
	JsonNode originalItem,
	JsonNode correctedItem,
	String resolutionNotes,
	LocalDateTime processedAt
) {
}
