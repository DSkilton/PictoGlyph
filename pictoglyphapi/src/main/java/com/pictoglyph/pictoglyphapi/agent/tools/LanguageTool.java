package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LanguageTool implements AgentTool {

	private static final double LANGUAGE_NOT_FOUND_CONFIDENCE = 0.10;
	private static final double LANGUAGE_WITH_SCRIPT_AND_DATE_CONFIDENCE = 0.75;
	private static final double LANGUAGE_WITH_SCRIPT_CONFIDENCE = 0.65;
	private static final double PARTIAL_LANGUAGE_CONFIDENCE = 0.40;

	private final LanguageRepository languageRepository;

	@Override
	public String getName() {
		return "LanguageTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		if (context.languageId() == null) {
			return List.of();
		}

		Optional<Language> optionalLanguage =
				languageRepository.findById(context.languageId());

		if (optionalLanguage.isEmpty()) {
			return List.of(buildNotFoundEvidence(context.languageId()));
		}

		Language language = optionalLanguage.get();

		return List.of(
				Evidence.builder()
						.source(getName())
						.description(buildLanguageEvidenceDescription(language))
						.confidence(calculateConfidence(language))
						.build()
		);
	}

	private Evidence buildNotFoundEvidence(Long languageId) {
		return Evidence.builder()
				.source(getName())
				.description("No language found for id: " + languageId)
				.confidence(LANGUAGE_NOT_FOUND_CONFIDENCE)
				.build();
	}

	private String buildLanguageEvidenceDescription(Language language) {
		return "Language '%s' uses script '%s'%s."
				.formatted(
						language.getName(),
						language.getScriptName(),
						buildDateRangeText(language)
				);
	}

	private String buildDateRangeText(Language language) {
		Integer dateStart = language.getDateStart();
		Integer dateEnd = language.getDateEnd();

		if (dateStart == null && dateEnd == null) {
			return "";
		}

		if (dateStart != null && dateEnd != null) {
			return " and has an estimated date range from "
					+ dateStart
					+ " to "
					+ dateEnd;
		}

		if (dateStart != null) {
			return " and has an estimated start date of " + dateStart;
		}

		return " and has an estimated end date of " + dateEnd;
	}

	private double calculateConfidence(Language language) {
		boolean hasName = hasText(language.getName());
		boolean hasScript = hasText(language.getScriptName());
		boolean hasDateEvidence = hasDateEvidence(language);

		if (hasName && hasScript && hasDateEvidence) {
			return LANGUAGE_WITH_SCRIPT_AND_DATE_CONFIDENCE;
		}

		if (hasName && hasScript) {
			return LANGUAGE_WITH_SCRIPT_CONFIDENCE;
		}

		return PARTIAL_LANGUAGE_CONFIDENCE;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private boolean hasDateEvidence(Language language) {
		return language.getDateStart() != null || language.getDateEnd() != null;
	}
}