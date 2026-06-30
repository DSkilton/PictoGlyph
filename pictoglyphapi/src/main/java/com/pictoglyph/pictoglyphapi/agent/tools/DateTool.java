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
public class DateTool implements AgentTool {

	private final LanguageRepository languageRepository;

	@Override
	public String getName() {
		return "DateTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		if(context.languageId() == null) {
			return List.of();
		}

		Optional<Language> optionalLanguage = languageRepository.findById(context.languageId());

		if (optionalLanguage.isEmpty()) {
			return List.of(
					Evidence.builder()
						.source(getName())
						.description("No language found for date evidence using id: " + context.languageId())
						.confidence(0.10)
						.build()
				);
		}

		Language language = optionalLanguage.get();

		return List.of(
				Evidence.builder()
						.source(getName())
						.description(buildDateEvidenceDescription(language))
						.confidence(calculateConfidence(language))
						.build()
		);
	}

	private String buildDateEvidenceDescription(Language language) {
		Integer dateStart = language.getDateStart();
		Integer dateEnd = language.getDateEnd();

		if (dateStart != null && dateEnd != null) {
			return "Language '%s' has an estimated date range from %d to %d."
					.formatted(language.getName(), dateStart, dateEnd);
		}

		if (dateStart != null) {
			return "Language '%s' has an estimated start date of %d."
					.formatted(language.getName(), dateStart);
		}

		if (dateEnd != null) {
			return "Language '%s' has an estimated end date of %d."
					.formatted(language.getName(), dateEnd);
		}

		return "Language '%s' has no known date range recorded."
				.formatted(language.getName());
	}

	private double calculateConfidence(Language language) {
		boolean hasStart = language.getDateStart() != null;
		boolean hasEnd = language.getDateEnd() != null;

		if (hasStart && hasEnd) {
			return 0.80;
		}

		if (hasStart || hasEnd) {
			return 0.55;
		}

		return 0.25;
	}
}
