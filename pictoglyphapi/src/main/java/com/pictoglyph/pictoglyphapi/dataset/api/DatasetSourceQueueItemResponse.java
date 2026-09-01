package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;

public record DatasetSourceQueueItemResponse(
		int queueIndex,
		String sourceName,
		String sourceUrl,
		IngestionStatus ingestionStatus,
		Long ingestionJobId,
		int importedCount,
		int skippedCount,
		int manualProcessingCount,
		String errorMessage
) {

	public boolean successful() {
		return ingestionStatus != IngestionStatus.FAILED;
	}
}
