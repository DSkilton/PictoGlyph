
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record SourceMappingContext(
		String sourceName,
		String apiUrl,
		SourceSample sample,
		Set<String> discoveredFields
) {

	public SourceMappingContext {
		Objects.requireNonNull(sourceName);
		Objects.requireNonNull(apiUrl);
		Objects.requireNonNull(sample);
		Objects.requireNonNull(discoveredFields);

		discoveredFields = Collections.unmodifiableSet(new LinkedHashSet<>(discoveredFields));
	}
}
