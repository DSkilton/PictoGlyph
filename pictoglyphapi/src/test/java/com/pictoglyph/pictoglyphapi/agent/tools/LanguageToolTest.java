package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageToolTest {

	@Mock
	private LanguageRepository languageRepository;

	@InjectMocks
	private LanguageTool languageTool;

	@Test
	void executeShouldReturnNoEvidenceWhenLanguageIdIsNull() {
		AgentContext context = AgentContext.builder()
				.languageId(null)
				.question("What is the likely language context?")
				.build();

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(languageRepository);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenLanguageCannotBeFound() {
		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely language context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.empty());

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languageEvidence = evidence.get(0);

		assertThat(languageEvidence.source()).isEqualTo("LanguageTool");
		assertThat(languageEvidence.description())
				.isEqualTo("No language found for id: 1");
		assertThat(languageEvidence.confidence()).isEqualTo(0.10);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnHighConfidenceEvidenceWhenLanguageHasScriptAndDateRange() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely language context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languageEvidence = evidence.get(0);

		assertThat(languageEvidence.source()).isEqualTo("LanguageTool");
		assertThat(languageEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' uses script 'Egyptian hieroglyphs' and has an estimated date range from -3200 to 400.");
		assertThat(languageEvidence.confidence()).isEqualTo(0.75);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnMediumConfidenceEvidenceWhenLanguageHasScriptButNoDateRange() {
		Language language = Language.builder()
				.id(1L)
				.name("Latin")
				.scriptName("Latin alphabet")
				.dateStart(null)
				.dateEnd(null)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely language context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languageEvidence = evidence.get(0);

		assertThat(languageEvidence.source()).isEqualTo("LanguageTool");
		assertThat(languageEvidence.description())
				.isEqualTo("Language 'Latin' uses script 'Latin alphabet'.");
		assertThat(languageEvidence.confidence()).isEqualTo(0.65);

		verify(languageRepository).findById(1L);
	}
}