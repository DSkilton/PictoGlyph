package com.pictoglyph.pictoglyphapi.ml.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MlProcessingResponse(
		String contractVersion,
		Long jobId,
		Long symbolId,
		MlProcessingResultStatus status,
		List<MlModelResult> modelResults,
		Instant processedAt,
		String errorMessage
) {

	public MlProcessingResponse {
		modelResults = modelResults == null
				? List.of()
				: List.copyOf(modelResults);
	}
}
