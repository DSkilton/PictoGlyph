package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;

import java.util.Objects;

public record GptFieldSuggestion(
		SourceMappingTarget target,
		String sourceField,
		String reason
) {

	public GptFieldSuggestion {
		Objects.requireNonNull(target, "A mapping target is required");

		if (sourceField == null || sourceField.isBlank()) {
			throw new IllegalArgumentException("A source field is required");
		}

		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required");
		}

		sourceField = sourceField.trim();
		reason = reason.trim();
	}
}
