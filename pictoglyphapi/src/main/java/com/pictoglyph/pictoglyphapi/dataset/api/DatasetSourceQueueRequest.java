package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;

import java.util.List;

public record DatasetSourceQueueRequest(
		String datasetName,
		List<ApiIngestionRequest> sources
) {
}
