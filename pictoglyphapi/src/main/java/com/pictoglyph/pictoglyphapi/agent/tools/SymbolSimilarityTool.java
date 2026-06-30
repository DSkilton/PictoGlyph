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

		SymbolSimilarityResponse response = symbolSimilarityClient.findSimilarSymbols(context.symbolId());

		if (response == null || response.matches() == null || response.matches().isEmpty()) {
			return List.of(
					Evidence.builder()
							.source(getName())
							.description("No similar symbols found for symbol id: " + context.symbolId())
							.confidence(0.10)
							.build()
			);
		}

		return response.matches()
				.stream()
				.map(match -> toEvidence(response, match))
				.toList();
	}

	private Evidence toEvidence(SymbolSimilarityResponse response, SymbolSimilarityMatch match) {
		return Evidence.builder()
				.source(getName())
				.description("Smybol "
					+ response.querySymbolId()
					+ " is visually similar to symbol "
					+ match.symbolid()
					+ " using model "
					+ response.model()
					+ ". Similarity score: "
					+ match.similarity()
					+ ".")
				.confidence(match.similarity())
				.build();
	}
}
