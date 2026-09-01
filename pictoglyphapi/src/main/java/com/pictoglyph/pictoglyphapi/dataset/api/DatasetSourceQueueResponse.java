package com.pictoglyph.pictoglyphapi.dataset.api;

import java.util.List;

public record DatasetSourceQueueResponse(
		DatasetPreparationResponse dataset,
		int sourceCount,
		int completedSourceCount,
		int failedSourceCount,
		List<DatasetSourceQueueItemResponse> sources
) {
}
