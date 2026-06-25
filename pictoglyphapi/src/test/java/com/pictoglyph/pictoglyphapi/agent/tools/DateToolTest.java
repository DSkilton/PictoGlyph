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
public class DateToolTest {

	@Mock
	private LanguageRepository languageRepository;

	@InjectMocks
	private DateTool dateTool;

	@Test
	void executeShouldReturnNoEvidenceWhenLanguageIdIsNull() {
		AgentContext context = AgentContext.builder()
				.languageId(null)
				.question("What is the likely date context?")
				.build();

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(languageRepository);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenLanguageCannotBeFound() {
		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely date context")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.empty());

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence dateEvidence = evidence.get(0);

		assertThat(dateEvidence.source()).isEqualTo("DateTool");
		assertThat(dateEvidence.description())
				.isEqualTo("No language found for date evidence using id: 1");
		assertThat(dateEvidence.confidence()).isEqualTo(0.10);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnHighConfidenceEvidenceWhenLanguageHasStartAndEndDates() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely date context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence dateEvidence = evidence.get(0);

		assertThat(dateEvidence.source()).isEqualTo("DateTool");
		assertThat(dateEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' has an estimated date range from -3200 to 400.");
		assertThat(dateEvidence.confidence()).isEqualTo(0.80);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnMediumConfidenceEvidenceWhenLanguageOnlyHasStartDate() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(-3200)
				.dateEnd(null)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely date context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence dateEvidence = evidence.get(0);

		assertThat(dateEvidence.source()).isEqualTo("DateTool");
		assertThat(dateEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' has an estimated start date of -3200.");
		assertThat(dateEvidence.confidence()).isEqualTo(0.55);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnMediumConfidenceEvidenceWhenLanguageOnlyHasEndDate() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(null)
				.dateEnd(400)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely date context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence dateEvidence = evidence.get(0);

		assertThat(dateEvidence.source()).isEqualTo("DateTool");
		assertThat(dateEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' has an estimated end date of 400.");
		assertThat(dateEvidence.confidence()).isEqualTo(0.55);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenLanguageHasNoDates() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.dateStart(null)
				.dateEnd(null)
				.build();

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What is the likely date context?")
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		List<Evidence> evidence = dateTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence dateEvidence = evidence.get(0);

		assertThat(dateEvidence.source()).isEqualTo("DateTool");
		assertThat(dateEvidence.description())
				.isEqualTo("Language 'Ancient Egyptian' has no known date range recorded.");
		assertThat(dateEvidence.confidence()).isEqualTo(0.25);

		verify(languageRepository).findById(1L);
	}
}
