package com.pictoglyph.pictoglyphapi.dataset.api;

import java.util.List;

public record DatasetSourceProfileQueueResponse(
		DatasetPreparationResponse dataset,
		int sourceCount,
		int completedSourceCount,
		int failedSourceCount,
		List<DatasetSourceProfileQueueItemResponse> sources
) {
}
