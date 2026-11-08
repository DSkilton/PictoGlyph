
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SourceMappingPipeline {
	private static final Logger LOGGER = LoggerFactory.getLogger(SourceMappingPipeline.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final List<SourceMappingStrategy> strategies;

	public Map<SourceMappingTarget, FieldMatch> resolve(SourceMappingContext context) {
		Objects.requireNonNull(context);

		EnumMap<SourceMappingTarget, FieldMatch> matches = new EnumMap<>(SourceMappingTarget.class);

		for (SourceMappingStrategy strategy : strategies) {
			Map<SourceMappingTarget, FieldMatch> snapshot = Collections.unmodifiableMap(
							new EnumMap<>(matches)
					);

			MappingStep step = Objects.requireNonNull(
					strategy.apply(context, snapshot), "Mapping strategy returned null"
			);
			
			matches.clear();
			matches.putAll(step.matches());

			long mappedTargets = matches.values().stream()
					.filter(match ->
							match != null &&
									match.sourceField() != null &&
									!match.sourceField().isBlank()
					)
					.count();

			LOGGER.info("Mapping strategy completed: strategy={}, mappedTargets={}, stop={}", strategy.id(), mappedTargets, step.stop());
			PROVENANCE_LOGGER.info("event=source_mapping_strategy_completed strategy={} mappedTargets={} stop={} status=PROPOSED", strategy.id(), mappedTargets, step.stop());

			if (step.stop()) {
				break;
			}
		}

		return Collections.unmodifiableMap(matches);
	}
}
