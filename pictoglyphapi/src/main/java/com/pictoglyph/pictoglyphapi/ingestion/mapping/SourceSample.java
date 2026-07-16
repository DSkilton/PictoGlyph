package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SourceSample(
		String itemArrayField,
		List<JsonNode> sampleItems
) {

	public SourceSample {
		sampleItems = sampleItems == null
				? List.of()
				: List.copyOf(sampleItems);
	}
}
