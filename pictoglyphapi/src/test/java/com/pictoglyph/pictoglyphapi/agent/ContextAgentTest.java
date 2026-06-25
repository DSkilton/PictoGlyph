package com.pictoglyph.pictoglyphapi.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAgentTest {

	@Test
	void investigateShouldCollectEvidenceAndCreateHypothesis() {
		ContextAgent contextAgent = new ContextAgent(List.of(
				fakeTool("SymbolSimilarityTool", "Fake symbol similarity evidence", 0.85),
				fakeTool("LanguageTool", "Fake language evidence", 0.70),
				fakeTool("PlaceTool", "Fake place evidence", 0.65),
				fakeTool("DateTool", "Fake date evidence", 0.75)
		));

		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.languageId(1L)
				.placeId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(4);
		assertThat(result.hypotheses()).hasSize(1);

		Hypothesis hypothesis = result.hypotheses().get(0);

		assertThat(hypothesis.supportingEvidence()).hasSize(4);
		assertThat(hypothesis.contradictingEvidence()).isEmpty();
		assertThat(hypothesis.conclusion())
				.isEqualTo("Most likely interpretation requires further evidence.");

		assertThat(hypothesis.confidence())
				.isEqualTo(0.7375);

		assertThat(result.evidence())
				.extracting(Evidence::source)
				.containsExactly(
						"SymbolSimilarityTool",
						"DateTool",
						"LanguageTool",
						"PlaceTool"
				);
	}

	private AgentTool fakeTool(String name, String description, double confidence) {
		return new AgentTool() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public List<Evidence> execute(AgentContext context) {
				return List.of(
						Evidence.builder()
								.source(getName())
								.description(description)
								.confidence(confidence)
								.build()
				);
			}
		};
	}
}