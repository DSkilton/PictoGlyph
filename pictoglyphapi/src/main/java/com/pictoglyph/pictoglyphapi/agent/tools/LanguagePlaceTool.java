package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.LanguagePlace;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguagePlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LanguagePlaceTool implements AgentTool {

	private final LanguagePlaceRepository languagePlaceRepository;

	@Override
	public String getName() {
		return "LanguagePlaceTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		if (context.languageId() == null || context.placeId() == null) {
			return List.of();
		}

		Optional<LanguagePlace> optionalLanguagePlace =
				languagePlaceRepository.findByLanguage_IdAndPlace_Id(context.languageId(), context.placeId());

		if (optionalLanguagePlace.isEmpty()) {
			return List.of(
					Evidence.builder()
							.source(getName())
							.description("No language-place association found for language id "
									+ context.languageId()
									+ " and place id "
									+ context.placeId()
									+ ".")
							.confidence(0.10)
							.build()
			);
		}

		LanguagePlace languagePlace = optionalLanguagePlace.get();

		return List.of(
				Evidence.builder()
						.source(getName())
						.description(buildDescription(languagePlace))
						.confidence(calculateConfidence(languagePlace))
						.build()
		);
	}

	private String buildDescription(LanguagePlace languagePlace) {
		return "Language '%s' is associated with place '%s' in '%s'%s. Recorded association confidence: %s."
				.formatted(
						languagePlace.getLanguage().getName(),
						languagePlace.getPlace().getName(),
						languagePlace.getPlace().getCountry(),
						buildDateRangeText(languagePlace),
						buildRecordedConfidenceText(languagePlace)
				);
	}

	private String buildDateRangeText(LanguagePlace languagePlace) {
		Integer dateStart = languagePlace.getDateStart();
		Integer dateEnd = languagePlace.getDateEnd();

		if (dateStart != null && dateEnd != null) {
			return " from " + dateStart + " to " + dateEnd;
		}

		if (dateStart != null) {
			return " from " + dateStart + " onward";
		}

		if (dateEnd != null) {
			return " until " + dateEnd;
		}

		return "";
	}

	private String buildRecordedConfidenceText(LanguagePlace languagePlace) {
		if (languagePlace.getConfidence() == null) {
			return "not recorded";
		}

		return languagePlace.getConfidence() + "%";
	}

	private double calculateConfidence(LanguagePlace languagePlace) {
		if (languagePlace.getConfidence() == null) {
			return 0.50;
		}

		double normalisedConfidence = languagePlace.getConfidence() / 100.0;

		if (normalisedConfidence < 0.0) {
			return 0.0;
		}

		if (normalisedConfidence > 1.0) {
			return 1.0;
		}

		return normalisedConfidence;
	}
}