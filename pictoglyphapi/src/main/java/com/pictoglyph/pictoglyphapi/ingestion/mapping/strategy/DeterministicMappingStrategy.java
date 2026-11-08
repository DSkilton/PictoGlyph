
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.MappingCompleteness;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldMatcher;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@Order(100)
@RequiredArgsConstructor
public class DeterministicMappingStrategy
		implements SourceMappingStrategy {

	private final SourceFieldMatcher matcher;

	@Override
	public String id() {
		return "DETERMINISTIC";
	}

	@Override
	public MappingStep apply(SourceMappingContext context, Map<SourceMappingTarget, FieldMatch> currentMatches) {
		Map<SourceMappingTarget, FieldMatch> combined = new EnumMap<>(SourceMappingTarget.class);

		combined.putAll(currentMatches);

		if (MappingCompleteness.hasRequiredMappings(combined)) {
			return MappingStep.stopWith(combined);
		}

		Map<SourceMappingTarget, FieldMatch> proposed = matcher.matchFields(
						context.discoveredFields(),
						context.sample().sampleItems()
				);

		Set<String> usedFields = new HashSet<>();

		for (FieldMatch match : combined.values()) {
			if (match != null && match.sourceField() != null) {
				usedFields.add(match.sourceField());
			}
		}

		for (Map.Entry<SourceMappingTarget, FieldMatch> entry : proposed.entrySet()) {
			FieldMatch existing = combined.get(entry.getKey());
			FieldMatch candidate = entry.getValue();

			if (existing != null
					&& existing.sourceField() != null) {
				continue;
			}

			if (candidate == null || candidate.sourceField() == null || usedFields.contains(candidate.sourceField())) {
				continue;
			}

			combined.put(entry.getKey(), candidate);
			usedFields.add(candidate.sourceField());
		}

		if (MappingCompleteness.hasRequiredMappings(combined)) {
			return MappingStep.stopWith(combined);
		}

		return MappingStep.continueWith(combined);
	}
}
