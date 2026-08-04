package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;

public record MlJobQueueResult(
		MlProcessingJob job,
		boolean created
) {
}
