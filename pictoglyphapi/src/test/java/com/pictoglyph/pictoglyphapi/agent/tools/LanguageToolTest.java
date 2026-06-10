package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LanguageToolTest {

	@Mock
	private LanguageRepository languageRepository;

	@Test
	void executeShouldReturnLanguageEvidenceWhenLanguageExists() {
		Language language = Language.builder()
				.id(1L)
				.name("Egyptian")
				.scriptName("Hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));

		LanguageTool languageTool = new LanguageTool(languageRepository);

		AgentContext context = AgentContext.builder()
				.languageId(1L)
				.question("What are the most likely interpretations?")
				.build();

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence languageEvidence = evidence.get(0);

		assertThat(languageEvidence.source()).isEqualTo("LanguageTool");
		assertThat(languageEvidence.description())
				.contains("Egyptian")
				.contains("Hieroglyphs")
				.contains("-3200")
				.contains("400");

		assertThat(languageEvidence.confidence()).isEqualTo(0.75);

		verify(languageRepository).findById(1L);
	}

	@Test
	void executeShouldReturnEmptyEvidenceWhenNoLanguageIdProvided() {
		LanguageTool languageTool = new LanguageTool(languageRepository);

		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.question("What are the most likely interpretations")
				.build();

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(languageRepository);
	}

	@Test
	void executeShouldReturnLowConfidenceWhenLanguageDoesNotExist() {
		when(languageRepository.findById(99L)).thenReturn(Optional.empty());

		LanguageTool languageTool = new LanguageTool(languageRepository);

		AgentContext context = AgentContext.builder()
				.languageId(99L)
				.question("What are the most likely interpretations")
				.build();

		List<Evidence> evidence = languageTool.execute(context);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).source()).isEqualTo("LanguageTool");
		assertThat(evidence.get(0).description()).contains("No language found");
		assertThat(evidence.get(0).confidence()).isEqualTo(0.10);

		verify(languageRepository).findById(99L);

	}
}
