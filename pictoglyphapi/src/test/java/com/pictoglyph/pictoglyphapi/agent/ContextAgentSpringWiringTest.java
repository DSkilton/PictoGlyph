package com.pictoglyph.pictoglyphapi.agent;

import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityClient;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityMatch;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityResponse;
import com.pictoglyph.pictoglyphapi.agent.tools.DateTool;
import com.pictoglyph.pictoglyphapi.agent.tools.LanguagePlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.LanguageTool;
import com.pictoglyph.pictoglyphapi.agent.tools.PlaceTool;
import com.pictoglyph.pictoglyphapi.agent.tools.SymbolSimilarityTool;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.LanguagePlace;
import com.pictoglyph.pictoglyphapi.entities.core.Place;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguagePlaceRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
		ContextAgent.class,
		DateTool.class,
		LanguageTool.class,
		PlaceTool.class,
		LanguagePlaceTool.class,
		SymbolSimilarityTool.class,
		ContextAgentSpringWiringTest.TestConfig.class
})
class ContextAgentSpringWiringTest {

	@Autowired
	private ContextAgent contextAgent;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private LanguagePlaceRepository languagePlaceRepository;

	@Autowired
	private SymbolSimilarityClient symbolSimilarityClient;

	@BeforeEach
	void setUp() {
		Language language = Language.builder()
				.id(1L)
				.name("Egyptian")
				.scriptName("Hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		Place place = Place.builder()
				.id(1L)
				.name("Memphis")
				.country("Egypt")
				.latitude(new BigDecimal("29.8440"))
				.longitude(new BigDecimal("31.2510"))
				.build();

		LanguagePlace languagePlace = LanguagePlace.builder()
				.id(1L)
				.language(language)
				.place(place)
				.dateStart(-3200)
				.dateEnd(400)
				.confidence(90)
				.build();

		SymbolSimilarityResponse symbolSimilarityResponse =
				new SymbolSimilarityResponse(
						1L,
						"mock-symbol-similarity-v1",
						List.of(new SymbolSimilarityMatch(2L, 0.87))
				);

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
		when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
		when(languagePlaceRepository.findByLanguage_IdAndPlace_Id(1L, 1L))
				.thenReturn(Optional.of(languagePlace));
		when(symbolSimilarityClient.findSimilarSymbols(1L))
				.thenReturn(symbolSimilarityResponse);
	}

	@Test
	void springShouldInjectAllAgentToolsIntoContextAgent() {
		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.languageId(1L)
				.placeId(1L)
				.question("What are the most likely interpretations?")
				.build();

		AgentResult result = contextAgent.investigate(context);

		assertThat(result.evidence()).hasSize(5);

		assertThat(result.evidence())
				.extracting(Evidence::source)
				.contains(
						"SymbolSimilarityTool",
						"DateTool",
						"LanguageTool",
						"PlaceTool",
						"LanguagePlaceTool"
				);

		assertThat(result.hypotheses()).hasSize(1);
		assertThat(result.hypotheses().get(0).supportingEvidence()).hasSize(5);
		assertThat(result.hypotheses().get(0).confidence()).isBetween(0.0, 1.0);
	}

	@Configuration
	static class TestConfig {

		@Bean
		LanguageRepository languageRepository() {
			return Mockito.mock(LanguageRepository.class);
		}

		@Bean
		PlaceRepository placeRepository() {
			return Mockito.mock(PlaceRepository.class);
		}

		@Bean
		LanguagePlaceRepository languagePlaceRepository() {
			return Mockito.mock(LanguagePlaceRepository.class);
		}

		@Bean
		SymbolSimilarityClient symbolSimilarityClient() {
			return Mockito.mock(SymbolSimilarityClient.class);
		}
	}
}