package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityClient;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityMatch;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SymbolSimilarityTool implements AgentTool {

	private static final double NO_SIMILAR_SYMBOLS_CONFIDENCE = 0.10;
	private static final double HIGH_SIMILARITY_THRESHOLD = 0.90;
	private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.80;
	private static final double LOW_SIMILARITY_THRESHOLD = 0.70;

	private static final double HIGH_SIMILARITY_CONFIDENCE = 0.85;
	private static final double MEDIUM_SIMILARITY_CONFIDENCE = 0.75;
	private static final double LOW_SIMILARITY_CONFIDENCE = 0.60;
	private static final double WEAK_SIMILARITY_CONFIDENCE = 0.35;

	private final SymbolSimilarityClient symbolSimilarityClient;

	@Override
	public String getName() {
		return "SymbolSimilarityTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		if (context.symbolId() == null) {
			return List.of();
		}

		SymbolSimilarityResponse response =
				symbolSimilarityClient.findSimilarSymbols(context.symbolId());

		if (hasNoMatches(response)) {
			return List.of(buildNoMatchesEvidence(context.symbolId()));
		}

		return response.matches()
				.stream()
				.map(match -> toEvidence(response, match))
				.toList();
	}

	private boolean hasNoMatches(SymbolSimilarityResponse response) {
		return response == null
				|| response.matches() == null
				|| response.matches().isEmpty();
	}

	private Evidence buildNoMatchesEvidence(Long symbolId) {
		return Evidence.builder()
				.source(getName())
				.description("No similar symbols found for symbol id: " + symbolId)
				.confidence(NO_SIMILAR_SYMBOLS_CONFIDENCE)
				.build();
	}

	private Evidence toEvidence(
			SymbolSimilarityResponse response,
			SymbolSimilarityMatch match
	) {
		return Evidence.builder()
				.source(getName())
				.description("Symbol "
						+ response.querySymbolId()
						+ " is visually similar to symbol "
						+ match.symbolId()
						+ " using model "
						+ response.model()
						+ ". Similarity score: "
						+ match.similarity()
						+ ".")
				.confidence(calculateConfidence(match.similarity()))
				.build();
	}

	private double calculateConfidence(Double similarity) {
		if (similarity == null) {
			return NO_SIMILAR_SYMBOLS_CONFIDENCE;
		}

		if (similarity >= HIGH_SIMILARITY_THRESHOLD) {
			return HIGH_SIMILARITY_CONFIDENCE;
		}

		if (similarity >= MEDIUM_SIMILARITY_THRESHOLD) {
			return MEDIUM_SIMILARITY_CONFIDENCE;
		}

		if (similarity >= LOW_SIMILARITY_THRESHOLD) {
			return LOW_SIMILARITY_CONFIDENCE;
		}

		return WEAK_SIMILARITY_CONFIDENCE;
	}
}