package com.pictoglyph.pictoglyphapi.ml.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlModelResult(
		String modelName,
		String modelVersion,
		int embeddingDimension,
		List<Double> embedding,
		JsonNode preprocessing
) {

	public MlModelResult {
		embedding = embedding == null
				? List.of()
				: List.copyOf(embedding);

		preprocessing = preprocessing == null
				? JsonNodeFactory.instance.objectNode()
				: preprocessing.deepCopy();
	}
}
