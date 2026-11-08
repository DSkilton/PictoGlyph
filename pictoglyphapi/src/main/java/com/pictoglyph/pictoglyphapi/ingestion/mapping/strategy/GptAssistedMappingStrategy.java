
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.llm.GptMappingFallbackService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.MappingCompleteness;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(200)
@RequiredArgsConstructor
public class GptAssistedMappingStrategy implements SourceMappingStrategy {

	private final GptMappingFallbackService fallbackService;

	@Override
	public String id() {
		return "GPT_FALLBACK";
	}

	@Override
	public MappingStep apply(SourceMappingContext context, Map<SourceMappingTarget, FieldMatch> currentMatches) {

		if (MappingCompleteness.hasRequiredMappings(currentMatches)) {
			return MappingStep.stopWith(currentMatches);
		}

		Map<SourceMappingTarget, FieldMatch> enriched = fallbackService.enrich(
						context.sourceName(),
						context.discoveredFields(),
						context.sample().sampleItems(),
						currentMatches
				);

		return MappingStep.continueWith(enriched);
	}
}
