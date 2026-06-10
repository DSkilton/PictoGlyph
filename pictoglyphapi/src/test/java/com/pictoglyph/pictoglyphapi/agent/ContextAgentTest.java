package com.pictoglyph.pictoglyphapi.agent;

import com.pictoglyph.pictoglyphapi.agent.tools.DateTool;
import com.pictoglyph.pictoglyphapi.agent.tools.PlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.SymbolSimilarityTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAgentTest {

	@Test
	void investigateShouldCollectEvidenceAndCreateHypothesis() {
		AgentTool fakeLanguageTool = new AgentTool() {
			@Override
			public String getName() {
				return "LanguageTool";
			}

			@Override
			public List<Evidence> execute(AgentContext context) {
				return List.of(
						Evidence.builder()
								.source(getName())
								.description("Fake language evidence")
								.confidence(0.70)
								.build()
				);
			}
		};

		ContextAgent contextAgent = new ContextAgent(List.of(
				new SymbolSimilarityTool(),
				fakeLanguageTool,
				new PlaceTool(),
				new DateTool()
		));

		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.languageId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(4);
		assertThat(result.hypotheses()).hasSize(1);
		assertThat(result.hypotheses().get(0).supportingEvidence()).hasSize(4);
	}

}