package com.pictoglyph.pictoglyphapi.ml.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlProcessingRequest(
		@NotBlank
		String contractVersion,

		@NotNull
		Long jobId,

		@NotNull
		Long symbolId,

		@NotNull
		MlProcessingTaskType taskType,

		@NotBlank
		String modelProfile,

		@NotBlank
		String imagePath,

		@NotBlank
		String inputChecksum,

		String symbolCode,

		Long languageId,

		JsonNode metadata
) {

	public MlProcessingRequest {
		metadata = metadata == null
				? JsonNodeFactory.instance.objectNode()
				: metadata.deepCopy();
	}
}
