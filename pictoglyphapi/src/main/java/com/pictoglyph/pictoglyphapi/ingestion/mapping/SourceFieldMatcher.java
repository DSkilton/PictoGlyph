package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.JsonNodePathReader.read;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingCandidateCatalog.candidatesFor;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JPEG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JPG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.PNG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.SVG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.TIF;
import static com.pictoglyph.pictoglyphapi.utils.Constants.TIFF;
import static com.pictoglyph.pictoglyphapi.utils.Constants.WEBP;

@Service
public class SourceFieldMatcher {

	private static final double EXACT_NAME_SCORE = 0.85;
	private static final double NORMALISED_NAME_SCORE = 0.75;

	private static final double IMAGE_VALUE_BONUS = 0.15;
	private static final double CODE_VALUE_BONUS = 0.10;
	private static final double DATE_VALUE_BONUS = 0.10;
	private static final double PLACE_VALUE_BONUS = 0.05;

	private static final double REQUIRED_PATTERN_RATIO = 0.50;

	public Map<SourceMappingTarget, FieldMatch> matchFields(Set<String> discoveredFields, List<JsonNode> sampleItems ) {
		Set<String> discFields = discoveredFields == null ? Set.of() : discoveredFields;
		List<JsonNode> discItems = sampleItems == null ? List.of() : sampleItems;

		Map<SourceMappingTarget, FieldMatch> matches = new EnumMap<>(SourceMappingTarget.class);
		Set<String> usedSourceFields = new HashSet<>();

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			FieldMatch match = findBestField(target, discFields, discItems, usedSourceFields);

			matches.put(target, match);

			if (match.sourceField() != null) {
				usedSourceFields.add(match.sourceField());
			}
		}

		return matches;
	}

	private FieldMatch findBestField(SourceMappingTarget target, Set<String> discoveredFields, List<JsonNode> sampleItems, Set<String> usedSourceFields) {
		FieldMatch bestMatch = null;
		int bestPathDepth = Integer.MAX_VALUE;

		for (String discoveredField : discoveredFields) {
			if (usedSourceFields.contains(discoveredField)) {
				continue;
			}

			double nameScore = calculateNameScore(discoveredField, candidatesFor(target));

			if (nameScore == 0.0) {
				continue;
			}

			double valueScore = calculateValueScore(target, discoveredField, sampleItems);
			double confidence = round(Math.min(1.0, nameScore + valueScore));
			FieldMatch candidateMatch = new FieldMatch(target, discoveredField, confidence, buildMatchReason(nameScore, valueScore));
			int pathDepth = pathDepth(discoveredField);

			if (bestMatch == null || confidence > bestMatch.confidence() || (confidence == bestMatch.confidence() && pathDepth < bestPathDepth)) {
				bestMatch = candidateMatch;
				bestPathDepth = pathDepth;
			}
		}

		if (bestMatch == null) {
			return new FieldMatch(
					target,
					null,
					0.0,
					"No suitable source field found"
			);
		}

		return bestMatch;
	}

	private double calculateNameScore(String discoveredField, List<String> candidateNames) {
		String leafFieldName = lastPathPart(discoveredField);

		for (String candidateName : candidateNames) {
			if (leafFieldName.equals(candidateName)) {
				return EXACT_NAME_SCORE;
			}
		}

		String normalisedFieldName = normalise(leafFieldName);

		for (String candidateName : candidateNames) {
			if (normalisedFieldName.equals(normalise(candidateName))) {
				return NORMALISED_NAME_SCORE;
			}
		}

		return 0.0;
	}

	private double calculateValueScore(SourceMappingTarget target, String sourceField, List<JsonNode> sampleItems) {
		List<String> sampleValues = sampleValuesFor(sourceField, sampleItems);

		if (sampleValues.isEmpty()) {
			return 0.0;
		}

		return switch (target) {
			case IMAGE_PATH -> patternBonus(
					sampleValues,
					this::looksLikeImageReference,
					IMAGE_VALUE_BONUS
			);

			case SYMBOL_CODE -> patternBonus(
					sampleValues,
					this::looksLikeSymbolCode,
					CODE_VALUE_BONUS
			);

			case DATE_START, DATE_END -> patternBonus(
					sampleValues,
					this::looksLikeDate,
					DATE_VALUE_BONUS
			);

			case PLACE -> patternBonus(
					sampleValues,
					this::looksLikePlace,
					PLACE_VALUE_BONUS
			);

			case TITLE, DESCRIPTION, PERIOD -> 0.0;
		};
	}

	private double patternBonus(List<String> values, Predicate<String> pattern, double availableBonus) {
		long matchingCount = values.stream()
				.filter(pattern)
				.count();

		double matchingRatio = (double) matchingCount / values.size();

		return matchingRatio >= REQUIRED_PATTERN_RATIO
				? availableBonus
				: 0.0;
	}

	private List<String> sampleValuesFor(String sourceField, List<JsonNode> sampleItems) {
		List<String> values = new ArrayList<>();

		for (JsonNode sampleItem : sampleItems) {
			JsonNode valueNode = read(sampleItem, sourceField);

			if (valueNode == null || valueNode.isNull()) {
				continue;
			}

			if (!valueNode.isTextual()
					&& !valueNode.isNumber()
					&& !valueNode.isBoolean()) {
				continue;
			}

			String value = valueNode.asText().trim();

			if (!value.isBlank()) {
				values.add(value);
			}
		}

		return values;
	}

	private boolean looksLikeImageReference(String value) {
		String lowerValue = value.trim().toLowerCase(Locale.ROOT);

		try {
			if (lowerValue.startsWith("http://") || lowerValue.startsWith("https://")) {
				String path = URI.create(lowerValue).getPath();

				return path != null && hasSupportedImageExtension(path);
			}
				return hasSupportedImageExtension(lowerValue);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private boolean hasSupportedImageExtension(String value) {
		String pathWithoutQuery = value.split("\\?", 2)[0];

		return pathWithoutQuery.endsWith(PNG)
				|| pathWithoutQuery.endsWith(JPG)
				|| pathWithoutQuery.endsWith(JPEG)
				|| pathWithoutQuery.endsWith(WEBP)
				|| pathWithoutQuery.endsWith(SVG)
				|| pathWithoutQuery.endsWith(TIF)
				|| pathWithoutQuery.endsWith(TIFF);
	}

	private boolean looksLikeSymbolCode(String value) {
		String trimmedValue = value.trim();

		return trimmedValue.length() <= 30 && trimmedValue.matches("[A-Za-z0-9_\\-:. ]+");
	}

	private boolean looksLikeDate(String value) {
		String trimmedValue = value.trim();

		return trimmedValue.matches("-?\\d{1,4}") || trimmedValue.matches("(?i).*\\b(?:BC|BCE|AD|CE|century|dynasty)\\b.*"
		);
	}

	private boolean looksLikePlace(String value) {
		String trimmedValue = value.trim();

		return trimmedValue.length() >= 3 && trimmedValue.length() <= 100 && trimmedValue.matches(".*[A-Za-z].*");
	}

	private String buildMatchReason(double nameScore, double valueScore) {
		List<String> reasons = new ArrayList<>();

		if (nameScore == EXACT_NAME_SCORE) {
			reasons.add("Exact field-name match");
		} else if (nameScore == NORMALISED_NAME_SCORE) {
			reasons.add("Normalised field-name match");
		}

		if (valueScore > 0.0) {
			reasons.add("Sample values match expected pattern");
		}

		return String.join("; ", reasons);
	}

	private String normalise(String value) {
		return value == null
				? ""
				: value.toLowerCase(Locale.ROOT)
				.replace("_", "")
				.replace("-", "")
				.replace(" ", "")
				.replace(".", "");
	}

	private String lastPathPart(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}

		int dotIndex = value.lastIndexOf(".");

		return dotIndex >= 0
				? value.substring(dotIndex + 1)
				: value;
	}

	private int pathDepth(String value) {
		if (value == null || value.isBlank()) {
			return Integer.MAX_VALUE;
		}

		return value.split("\\.").length;
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}