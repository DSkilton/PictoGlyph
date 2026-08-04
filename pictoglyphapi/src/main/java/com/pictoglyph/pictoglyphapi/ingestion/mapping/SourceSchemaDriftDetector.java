package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TreeSet;

@Service
public class SourceSchemaDriftDetector {

	public SourceSchemaComparison comparison(Set<String> approvedFields, Set<String> currentFields) {
		Set<String> approved = normalise(approvedFields);
		Set<String> current = normalise(currentFields);

		Set<String> addedFields = new TreeSet<>(current);
		addedFields.removeAll(approved);

		Set<String> removedFields = new TreeSet<>(approved);
		removedFields.removeAll(current);

		return new SourceSchemaComparison(addedFields.stream().toList(), removedFields.stream().toList());
	}

	private Set<String> normalise(Set<String> fields) {
		Set<String> normalisedFields = new TreeSet<>();

		if (fields == null) {
			return normalisedFields;
		}

		for (String field : fields) {
			if (field == null || field.isBlank()) {
				continue;
			}

			normalisedFields.add(field.trim());
		}

		return normalisedFields;
	}
}
