package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import java.util.List;

public record SourceSchemaComparison(
		List<String> addedFields,
		List<String> removedFields
) {

	public SourceSchemaComparison {
		addedFields = addedFields == null
				? List.of()
				: List.copyOf(addedFields);

		removedFields = removedFields == null
				? List.of()
				: List.copyOf(removedFields);
	}

	public boolean matches() {
		return addedFields().isEmpty() && removedFields.isEmpty();
	}

	public boolean hasBreakingChanges() {
		return !removedFields.isEmpty();
	}
}
