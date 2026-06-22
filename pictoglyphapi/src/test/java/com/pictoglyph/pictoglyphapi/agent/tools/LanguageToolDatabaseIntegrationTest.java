package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class LanguageToolDatabaseIntegrationTest {

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private LanguageTool languageTool;

	@Test
	void languageToolShouldReturnEvidenceFromRealDatabase() {
		Language language = Language.builder()
				.name("Egyptian")
				.scriptName("Hieroglyphs")
				.dateStart(-3200)
				.dateEnd(400)
				.build();

		Language savedLanguage = languageRepository.save(language);

		AgentContext context = AgentContext.builder()
				.languageId(savedLanguage.getId())
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
	}
}