package com.pictoglyph.pictoglyphapi.agent;

import com.pictoglyph.pictoglyphapi.agent.tools.DateTool;
import com.pictoglyph.pictoglyphapi.agent.tools.LanguageTool;
import com.pictoglyph.pictoglyphapi.agent.tools.PlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.SymbolSimilarityTool;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ContextAgentTest {

	@Test
	void investigateShouldCollectEvidenceAndCreateHypothesis() {
		ContextAgent contextAgent = new ContextAgent(List.of(
				new SymbolSimilarityTool(),
				new LanguageTool(),
				new PlaceTool(),
				new DateTool()
		));

		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(4);
		assertThat(result.hypotheses()).hasSize(1);
		assertThat(result.hypotheses().get(0).supportingEvidence()).hasSize(4);
	}

}