package com.pictoglyph.pictoglyphapi.agent;

import com.pictoglyph.pictoglyphapi.agent.tools.DateTool;
import com.pictoglyph.pictoglyphapi.agent.tools.LanguageTool;
import com.pictoglyph.pictoglyphapi.agent.tools.PlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.SymbolSimilarityTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
		ContextAgent.class,
		DateTool.class,
		LanguageTool.class,
		PlaceTool.class,
		SymbolSimilarityTool.class
})
public class ContextAgentSpringWiringTest {

	@Autowired
	private ContextAgent contextAgent;

	@Test
	void springShouldInjectAllAgentToolsIntoContextAgent() {
		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(4);

		assertThat(result.evidence())
				.extracting(Evidence::source)
				.containsExactlyInAnyOrder(
						"SymbolSimilarityTool",
						"DateTool",
						"LanguageTool",
						"PlaceTool"
				);

		assertThat(result.hypotheses()).hasSize(1);
		assertThat(result.hypotheses().get(0).supportingEvidence()).hasSize(4);
		assertThat(result.hypotheses().get(0).confidence()).isBetween(0.73, 0.74);

	}
}
