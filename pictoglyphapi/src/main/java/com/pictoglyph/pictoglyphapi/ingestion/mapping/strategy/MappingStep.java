
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record MappingStep(
		Map<SourceMappingTarget, FieldMatch> matches,
		boolean stop
) {

	public MappingStep {
		Objects.requireNonNull(matches);

		EnumMap<SourceMappingTarget, FieldMatch> copy = new EnumMap<>(SourceMappingTarget.class);

		copy.putAll(matches);

		matches = Collections.unmodifiableMap(copy);
	}

	public static MappingStep continueWith(Map<SourceMappingTarget, FieldMatch> matches) {
		return new MappingStep(matches, false);
	}

	public static MappingStep stopWith(Map<SourceMappingTarget, FieldMatch> matches) {
		return new MappingStep(matches, true);
	}
}
