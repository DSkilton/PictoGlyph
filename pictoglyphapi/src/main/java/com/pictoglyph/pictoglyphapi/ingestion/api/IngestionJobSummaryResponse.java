package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionStatus;

import java.time.LocalDateTime;

public record IngestionJobSummaryResponse (
		Long id,
		String sourceType,
		String sourcePath,
		IngestionStatus status,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		LocalDateTime createdAt,
		LocalDateTime completedAt
){
}
