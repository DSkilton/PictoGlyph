package com.pictoglyph.pictoglyphapi.agent;

import com.pictoglyph.pictoglyphapi.agent.tools.DateTool;
import com.pictoglyph.pictoglyphapi.agent.tools.LanguageTool;
import com.pictoglyph.pictoglyphapi.agent.tools.PlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.SymbolSimilarityTool;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
		ContextAgent.class,
		DateTool.class,
		LanguageTool.class,
		PlaceTool.class,
		SymbolSimilarityTool.class,
		ContextAgentSpringWiringTest.TestConfig.class
})
class ContextAgentSpringWiringTest {

	@Autowired
	private ContextAgent contextAgent;

	@Autowired
	private LanguageRepository languageRepository;

	@BeforeEach
	void setUp() {
		Language language = Language.builder()
				.id(1L)
				.name("Egyptian")
				.scriptName("Hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
	}

	@Test
	void springShouldInjectAllAgentToolsIntoContextAgent() {
		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.languageId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(4);

		assertThat(result.evidence())
				.extracting(Evidence::source)
				.contains(
						"SymbolSimilarityTool",
						"DateTool",
						"LanguageTool",
						"PlaceTool"
				);

		assertThat(result.hypotheses()).hasSize(1);
		assertThat(result.hypotheses().get(0).supportingEvidence()).hasSize(4);
		assertThat(result.hypotheses().get(0).confidence()).isBetween(0.0, 1.0);
	}

	@Configuration
	static class TestConfig {

		@Bean
		LanguageRepository languageRepository() {
			return Mockito.mock(LanguageRepository.class);
		}
	}
}