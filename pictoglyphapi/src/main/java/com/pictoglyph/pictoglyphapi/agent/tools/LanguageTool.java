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

		Optional<Language> optionalLanguage = languageRepository.findById(context.languageId());

		if (optionalLanguage.isEmpty()) {
			return List.of(
					Evidence.builder()
							.source(getName())
							.description("No language found for id: " + context.languageId())
							.confidence(0.10)
							.build()
			);
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
			return " and has an estimated date range from " + dateStart + " to " + dateEnd;
		}

		if (dateStart != null) {
			return " and has an estimated start date of " + dateStart;
		}

		return " and has an estimated end date of " + dateEnd;
	}

	private double calculateConfidence(Language language) {
		boolean hasName = language.getName() != null && !language.getName().isBlank();
		boolean hasScript = language.getScriptName() != null && !language.getScriptName().isBlank();
		boolean hasDateRange = language.getDateStart() != null || language.getDateEnd() != null;

		if (hasName && hasScript && hasDateRange) {
			return 0.75;
		}

		if (hasName && hasScript) {
			return 0.65;
		}

		return 0.40;
	}
}