package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceResult;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;

import java.time.LocalDateTime;

public record DatasetPreparationSourceResponse(
		Long id,
		Long ingestionJobId,
		String sourceType,
		String sourceName,
		String sourcePath,
		IngestionStatus ingestionStatus,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		String errorMessage,
		LocalDateTime recordedAt
) {
	public static DatasetPreparationSourceResponse from(DatasetPreparationSourceResult sourceResult) {
		return new DatasetPreparationSourceResponse(
				sourceResult.getId(),
				sourceResult.getIngestionJobId(),
				sourceResult.getSourceType(),
				sourceResult.getSourceName(),
				sourceResult.getSourcePath(),
				sourceResult.getIngestionStatus(),
				sourceResult.getImportedCount(),
				sourceResult.getSkippedCount(),
				sourceResult.getManualProcessingCount(),
				sourceResult.getErrorMessage(),
				sourceResult.getRecordedAt()
		);
	}
}
