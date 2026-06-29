package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.LanguagePlace;
import com.pictoglyph.pictoglyphapi.entities.core.Place;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguagePlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguagePlaceToolTest {

	public static final String WHAT_IS_THE_LANGUAGE_PLACE_CONTEXT = "What is the language-place context?";
	@Mock
	private LanguagePlaceRepository languagePlaceRepository;

	@InjectMocks
	private LanguagePlaceTool languagePlaceTool;

	@Test
	void executeShouldReturnNoEvidenceWhenLanguageIdIsNull() {
		AgentContext context = AgentContext.builder()
				.languageId(null)
				.placeId(1L)
				.question(WHAT_IS_THE_LANGUAGE_PLACE_CONTEXT)
				.build();

		List<Evidence> evidence = languagePlaceTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(languagePlaceRepository);
	}

	@Test
	void executeShouldReturnNoEvidenceWhenPlaceIdIsNull() {
		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.placeId(null)
				.question(WHAT_IS_THE_LANGUAGE_PLACE_CONTEXT)
				.build();

		List<Evidence> evidence = languagePlaceTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(languagePlaceRepository);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenAssociationCannotBeFound() {
		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.placeId(2L)
				.question(WHAT_IS_THE_LANGUAGE_PLACE_CONTEXT)
				.build();

		when(languagePlaceRepository.findByLanguage_IdAndPlace_Id(1L, 2L))
				.thenReturn(Optional.empty());

		List<Evidence> evidence = languagePlaceTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languagePlaceEvidence = evidence.get(0);

		assertThat(languagePlaceEvidence.source()).isEqualTo("LanguagePlaceTool");
		assertThat(languagePlaceEvidence.description())
				.isEqualTo("No language-place association found for language id 1 and place id 2.");
		assertThat(languagePlaceEvidence.confidence()).isEqualTo(0.10);

		verify(languagePlaceRepository).findByLanguage_IdAndPlace_Id(1L, 2L);
	}

	@Test
	void executeShouldReturnAssociationEvidenceWhenLanguagePlaceExists() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		Place place = Place.builder()
				.id(2L)
				.name("Memphis")
				.country("Egypt")
				.latitude(new BigDecimal("29.8440"))
				.longitude(new BigDecimal("31.2510"))
				.build();

		LanguagePlace languagePlace = LanguagePlace.builder()
				.id(3L)
				.language(language)
				.place(place)
				.dateStart(-3200)
				.dateEnd(400)
				.confidence(90)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.placeId(2L)
				.question(WHAT_IS_THE_LANGUAGE_PLACE_CONTEXT)
				.build();

		when(languagePlaceRepository.findByLanguage_IdAndPlace_Id(1L, 2L))
				.thenReturn(Optional.of(languagePlace));

		List<Evidence> evidence = languagePlaceTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languagePlaceEvidence = evidence.get(0);

		assertThat(languagePlaceEvidence.source()).isEqualTo("LanguagePlaceTool");
		assertThat(languagePlaceEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' is associated with place 'Memphis' in 'Egypt' from -3200 to 400. Recorded association confidence: 90%.");
		assertThat(languagePlaceEvidence.confidence()).isEqualTo(0.90);

		verify(languagePlaceRepository).findByLanguage_IdAndPlace_Id(1L, 2L);
	}
}