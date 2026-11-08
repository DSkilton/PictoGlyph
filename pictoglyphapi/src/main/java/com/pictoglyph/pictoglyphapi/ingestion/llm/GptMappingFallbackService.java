package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.MappingCompleteness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GptMappingFallbackService {

	private static final Logger LOGGER = LoggerFactory.getLogger(GptMappingFallbackService.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");
	private static final double GPT_CANDIDATE_SCORE = 0.50;

	private final GptMappingAdvisor advisor;
	private final SourceFieldValueReader valueReader;
	private final boolean enabled;

	public GptMappingFallbackService(GptMappingAdvisor advisor, SourceFieldValueReader valueReader, @Value("${pictoglyph.openai.mapping-fallback.enabled:false}") boolean enabled) {
		this.advisor = advisor;
		this.valueReader = valueReader;
		this.enabled = enabled;
	}

	public Map<SourceMappingTarget, FieldMatch> enrich(String sourceName, Set<String> discoveredFields, List<JsonNode> sampleItems, Map<SourceMappingTarget, FieldMatch> deterministicMatches) {
		if (!enabled || MappingCompleteness.hasRequiredMappings(deterministicMatches) || discoveredFields == null || discoveredFields.isEmpty() || sampleItems == null || sampleItems.isEmpty()) {
			return deterministicMatches;
		}

		List<GptFieldSuggestion> suggestions;

		try {
			suggestions = advisor.suggest(sourceName, discoveredFields);
		} catch (RuntimeException e) {
			LOGGER.warn("GPT mapping fallback failed: errorType={}", e.getClass().getSimpleName());
			PROVENANCE_LOGGER.info("event=source_mapping_gpt_fallback_failed result=deterministic_retained errorType={}", e.getClass().getSimpleName());
			return deterministicMatches;
		}

		if (suggestions == null || suggestions.isEmpty()) {
			LOGGER.info("GPT mapping fallback completed: suggested=0, accepted=0");
			PROVENANCE_LOGGER.info("event=source_mapping_gpt_fallback_completed suggested=0 accepted=0 status=PROPOSED");
			return deterministicMatches;
		}

		Map<SourceMappingTarget, FieldMatch> combined = new EnumMap<>(SourceMappingTarget.class);
		if (deterministicMatches != null) {
			combined.putAll(deterministicMatches);
		}

		Set<String> usedFields = new HashSet<>();
		for (FieldMatch match : combined.values()) {
			if (match != null && match.sourceField() != null) {
				usedFields.add(match.sourceField());
			}
		}

		int accepted = 0;

		for (GptFieldSuggestion suggestion : suggestions) {
			if (suggestion == null || suggestion.target() == null || !discoveredFields.contains(suggestion.sourceField())
					|| isMapped(combined.get(suggestion.target())) || usedFields.contains(suggestion.sourceField())) {
				continue;
			}

			long populated = sampleItems.stream()
					.map(item -> valueReader.readText(item, suggestion.sourceField()))
					.filter(value -> value != null && !value.isBlank())
					.count();

			if (populated * 2 < sampleItems.size() || !hasRequiredValueEvidence(suggestion.target(), suggestion.sourceField(), sampleItems)) {
				continue;
			}

			String reason = "GPT candidate; sample coverage " + populated + "/" + sampleItems.size() + "; " + suggestion.reason() + "; requires source mapping validation";
			combined.put(suggestion.target(), new FieldMatch(suggestion.target(), suggestion.sourceField(), GPT_CANDIDATE_SCORE, reason));
			usedFields.add(suggestion.sourceField());
			accepted++;
		}

		LOGGER.info("GPT mapping fallback completed: suggested={}, accepted={}, rejected={}", suggestions.size(), accepted, suggestions.size() - accepted);
		PROVENANCE_LOGGER.info("event=source_mapping_gpt_fallback_completed suggested={} accepted={} rejected={} status=PROPOSED", suggestions.size(), accepted, suggestions.size() - accepted);
		return combined;
	}

	private boolean isMapped(FieldMatch match) {
		return match != null && match.sourceField() != null && !match.sourceField().isBlank();
	}

	private boolean hasRequiredValueEvidence(SourceMappingTarget target, String field, List<JsonNode> items) {
		if (target != SourceMappingTarget.IMAGE_PATH && target != SourceMappingTarget.SYMBOL_CODE) {
			return true;
		}

		long usable = items.stream()
				.map(item -> valueReader.readText(item, field))
				.filter(value -> value != null && (target == SourceMappingTarget.IMAGE_PATH ? looksLikeImageReference(value) : looksLikeCode(value)))
				.count();

		return usable * 2 >= items.size();
	}

	private boolean looksLikeCode(String value) {
		return value.length() <= 30 && value.matches("[A-Za-z0-9][A-Za-z0-9_:. -]*");
	}

	private boolean looksLikeImageReference(String value) {
		try {
			String path = URI.create(value).getPath();
			if (path == null) {
				return false;
			}
			String lower = path.toLowerCase(Locale.ROOT);
			return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
					|| lower.endsWith(".webp") || lower.endsWith(".tif") || lower.endsWith(".tiff") || lower.endsWith(".svg");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
