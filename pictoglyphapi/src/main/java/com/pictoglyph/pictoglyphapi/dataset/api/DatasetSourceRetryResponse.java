package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;

import java.time.LocalDateTime;

public record DatasetSourceRetryResponse(
		Long datasetPreparationId,
		Long sourceResultId,
		int attemptNumber,
		String sourceName,
		String sourcePath,
		IngestionStatus ingestionStatus,
		Long ingestionJobId,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		String errorMessage,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		DatasetPreparationResponse dataset
) {

	public boolean successful() {
		return ingestionStatus != IngestionStatus.FAILED;
	}
}
