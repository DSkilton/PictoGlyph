
package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import java.util.Map;

public final class MappingCompleteness {

	private MappingCompleteness() {	}

	public static boolean hasRequiredMappings(Map<SourceMappingTarget, FieldMatch> matches) {

		if (matches == null) {
			return false;
		}

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			if (!target.requiredForImport()) {
				continue;
			}

			FieldMatch match = matches.get(target);

			if (match == null || match.sourceField() == null || match.sourceField().isBlank()) {
				return false;
			}
		}

		return true;
	}
}
