package com.pictoglyph.pictoglyphapi.dataset.api;

public record ReleaseDatasetToMlResponse(
		Long datasetPreparationId,
		String modelProfile,
		int symbolCount,
		int createdJobCount,
		int existingJobCount
) {
}
