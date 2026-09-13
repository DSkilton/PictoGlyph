package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;

public record DatasetSourceProfileQueueItemResponse(
		int queueIndex,
		Long profileId,
		Long languageId,
		String sourceName,
		String sourceUrl,
		IngestionStatus ingestionStatus,
		Long ingestionJobId,
		int imported,
		int skippedCount,
		int manualProcessingCount,
		String errorMessage
) {

	public boolean successful() {
		return ingestionStatus != IngestionStatus.FAILED;
	}
}
