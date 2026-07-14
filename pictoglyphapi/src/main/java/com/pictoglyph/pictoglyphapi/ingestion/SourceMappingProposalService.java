package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.api.MappingEvidenceResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalResponse;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingCandidateCatalog;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingCandidateCatalog.ITEM_ARRAY_FIELDS;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingCandidateCatalog.candidatesFor;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DATE_END;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DATE_START;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DESCRIPTION;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.IMAGE_PATH;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.PERIOD;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.PLACE;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.SYMBOL_CODE;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.TITLE;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JPEG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JPG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.PNG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.SVG;
import static com.pictoglyph.pictoglyphapi.utils.Constants.TIF;
import static com.pictoglyph.pictoglyphapi.utils.Constants.TIFF;
import static com.pictoglyph.pictoglyphapi.utils.Constants.WEBP;

@Service
@RequiredArgsConstructor
public class SourceMappingProposalService {

	private static final int MAX_SAMPLE_ITEMS = 5;
	private static final int MAX_NESTED_DEPTH = 3;

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	public SourceMappingProposalResponse proposeMapping(SourceMappingProposalRequest request) {
		try {
			String apiResponse = restTemplate.getForObject(request.apiUrl(), String.class);

			if (apiResponse == null || apiResponse.isBlank()) {
				throw new IllegalStateException("API returned an empty response");
			}

			JsonNode rootNode = objectMapper.readTree(apiResponse);
			String itemArrayField = findItemArrayField(rootNode, request.itemArrayField());
			List<JsonNode> sampleItems = findSampleItems(rootNode, itemArrayField);

			Set<String> discoveredFields = discoverFields(sampleItems);
			Map<SourceMappingTarget, FieldMatch> matches = proposedFieldMatches(discoveredFields, sampleItems);

			SourceFieldMapping proposedMapping = buildSourceFieldMapping(itemArrayField, matches);
			List<MappingEvidenceResponse> evidence = buildEvidence(matches);
			double overallConfidence = calculateOverallConfidence(matches);

			return new SourceMappingProposalResponse(
					request.sourceName(),
					request.apiUrl(),
					proposedMapping,
					overallConfidence,
					sampleItems.size(),
					new ArrayList<>(discoveredFields),
					evidence
			);
		} catch (Exception e) {
			throw new IllegalStateException("Could not propse source mapping for: " + request.apiUrl(), e);
		}
	}

	private SourceFieldMapping buildSourceFieldMapping(String itemArrayField, Map<SourceMappingTarget, FieldMatch> matches) {
		return new SourceFieldMapping(
				itemArrayField,
				sourceField(matches, SYMBOL_CODE),
				sourceField(matches, IMAGE_PATH),
				sourceField(matches, TITLE),
				sourceField(matches, DESCRIPTION),
				sourceField(matches, PLACE),
				sourceField(matches, PERIOD),
				sourceField(matches, DATE_START),
				sourceField(matches, DATE_END)
		);
	}

	private String sourceField(Map<SourceMappingTarget, FieldMatch> matches, SourceMappingTarget target) {
		FieldMatch match = matches.get(target);
		return  match == null ? null : match.sourceField();
	}

	private List<MappingEvidenceResponse> buildEvidence(Map<SourceMappingTarget, FieldMatch> matches) {
		return matches.values()
				.stream()
				.map(match -> new MappingEvidenceResponse(
						match.target().responseFieldName(),
						match.sourceField(),
						match.confidence(),
						match.reason()
				)).toList();
	}

	private double calculateOverallConfidence(Map<SourceMappingTarget, FieldMatch> matches) {
		if (matches.isEmpty()) {
			return 0.0;
		}

		double weightedTotal = 0.0;
		double totalWeight = 0.0;

		for (FieldMatch match : matches.values()) {
			double weight = match.target().confidenceWeight();

			weightedTotal += match.confidence() * weight;
			totalWeight += weight;
		}

		if (totalWeight == 0.0) {
			return 0.0;
		}

		return round(weightedTotal / totalWeight);
	}

	private Map<SourceMappingTarget, FieldMatch> proposedFieldMatches(Set<String> discoveredFields, List<JsonNode> sampleItems) {
		Map<SourceMappingTarget, FieldMatch> matches = new EnumMap<>(SourceMappingTarget.class);
		Set<String> alreadyUsedSourceFields = new HashSet<>();

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			FieldMatch match = findBestField(target, discoveredFields, sampleItems, alreadyUsedSourceFields);

			matches.put(target, match);

			if (match.sourceField() != null) {
				alreadyUsedSourceFields.add(match.sourceField());
			}
		}

		return matches;
	}

	private FieldMatch findBestField(SourceMappingTarget target, Set<String> discoveredFields, List<JsonNode> sampleItems, Set<String> alreadyUsedSourceFields) {
		List<FieldMatch> candidates = new ArrayList<>();

		for (String discoveredField : discoveredFields) {
			double nameScore = calculateNameScore(discoveredField, candidatesFor(target));
			double valueScore = calculateValueScore(target, discoveredField, sampleItems);
			double totalScore = Math.min(1.0, nameScore + valueScore);

			if (totalScore > 0.0) {
				String reason = buildMatchReason(nameScore, valueScore, alreadyUsedSourceFields.contains(discoveredField));

				if (alreadyUsedSourceFields.contains(discoveredField)) {
					totalScore = totalScore * 0.70;
				}

				candidates.add(new FieldMatch(target, discoveredField, round(totalScore), reason));
			}
		}

		return candidates.stream()
				.sorted((left, right) -> Double.compare(right.confidence(), left.confidence()))
				.findFirst()
				.orElse(new FieldMatch(target, null, 0.0, "No suitable source field found"));
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private String buildMatchReason(double nameScore, double valueScore, boolean fieldAlreadyUsed) {
		List<String> reasons = new ArrayList<>();

		if (nameScore >= 0.85) {
			reasons.add("Exact field-name match");
		} else if (nameScore >= 0.75) {
			reasons.add("Normalised field-name match");
		} else if (nameScore >= 0.55) {
			reasons.add("Partial field-name match");
		}

		if (valueScore > 0.0) {
			reasons.add("Sample values match expected pattern");
		}

		if (fieldAlreadyUsed) {
			reasons.add("Confidence reduced because field was already used for another mapping");
		}

		return String.join("; ", reasons);
	}

	private double calculateValueScore(SourceMappingTarget target, String sourceField, List<JsonNode> sampleItems) {
		List<String> sampleValues = sampleValuesFor(sourceField, sampleItems);


		if (sampleValues.isEmpty()) {
			return 0.0;
		}

		return switch (target) {
			case IMAGE_PATH -> looksLikeImageField(sampleValues) ? 0.15 : 0.0;
			case SYMBOL_CODE -> looksLikeCodeField(sampleValues) ? 0.10 : 0.0;
			case DATE_START, DATE_END -> looksLikeDateField(sampleValues) ? 0.10 : 0.0;
			case PLACE -> looksLikePlaceField(sampleValues) ? 0.05 :0.0;
			case TITLE, DESCRIPTION, PERIOD -> 0.0;
		};
	}

	private boolean looksLikeCodeField(List<String> values) {
		return values.stream().anyMatch(value -> {
			String trimmed = value.trim();


			return trimmed.length() <= 30
					&& trimmed.matches("[A-Za-z0-9_\\-:. ]+");
		});
	}

	private boolean looksLikeDateField(List<String> values) {
		return values.stream().anyMatch(value ->
				value.matches("-?\\d{1,4}")
						|| value.matches(".*\\b\\d{3,4}\\b.*")
		);
	}

	private boolean looksLikePlaceField(List<String> values) {
		return values.stream().anyMatch(value ->
				value.length() >= 3
				&& value.length() <= 80
				&& value.matches(".*[A-Za-z].*")
		);
	}

	private boolean looksLikeImageField(List<String> values) {
		return values.stream().anyMatch(value -> {
			String lowerValue = value.toLowerCase();

			return lowerValue.startsWith("http://")
					|| lowerValue.startsWith("https://")
					|| lowerValue.endsWith(PNG)
					|| lowerValue.endsWith(JPG)
					|| lowerValue.endsWith(JPEG)
					|| lowerValue.endsWith(WEBP)
					|| lowerValue.endsWith(SVG)
					|| lowerValue.endsWith(TIF)
					|| lowerValue.endsWith(TIFF);
		});
	}

	private List<String> sampleValuesFor(String sourceField, List<JsonNode> sampleItems) {
		List<String> values = new ArrayList<>();

		for (JsonNode item : sampleItems) {
			JsonNode valueNode = valueAtPath(item, sourceField);

			if (valueNode == null || valueNode.isNull()) {
				continue;
			}

			if (valueNode.isTextual() || valueNode.isNumber()) {
				String value = valueNode.asText();

				if (!value.isBlank()) {
					values.add(value);
				}
			}
		}

		return values;
	}

	private JsonNode valueAtPath(JsonNode node, String path) {
		if (node == null || path == null || path.isBlank()) {
			return null;
		}

		String[] parts = path.split("\\.");
		JsonNode current = node;

		for (String part : parts) {
			if (current == null || current.isNull()) {
				return null;
			}

			current = current.get(part);
		}

		return current;
	}

	private double calculateNameScore(String discoveredField, List<String> candidateNames) {
		String normalisedDiscoveredField = normalise(lastPathPart(discoveredField));

		for (String candidateName : candidateNames) {
			if (lastPathPart(discoveredField).equals(candidateName)) {
				return 0.85;
			}
		}

		for (String candidateName : candidateNames) {
			if (normalisedDiscoveredField.equals(normalise(candidateName))) {
				return 0.75;
			}
		}

		for (String canditeName : candidateNames) {
			String normalisedCandidateName = normalise(canditeName);

			if (normalisedDiscoveredField.contains(normalisedCandidateName) || normalisedCandidateName.contains(normalisedDiscoveredField)) {
				return 0.55;
			}
		}

		return 0.0;
	}

	private String normalise(String value) {
		return value == null
				? ""
				: value.toLowerCase()
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

	private Set<String> discoverFields(List<JsonNode> sampleItems) {
		Set<String> fields = new LinkedHashSet<>();

		for (JsonNode item : sampleItems) {
			discoverFields(item, "", fields, 0);

		}

		return fields;
	}

	private void discoverFields(JsonNode node, String prefix, Set<String> fields, int depth) {
		if (node == null || node.isNull() || !node.isObject() || depth >= MAX_NESTED_DEPTH) {
			return;
		}

		Iterator<String> fieldNames = node.fieldNames();

		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			String fullPath = prefix.isBlank()
					? fieldName
					: prefix + "." + fieldName;

			fields.add(fullPath);

			JsonNode childNode = node.get(fieldName);

			if (childNode != null && childNode.isObject()) {
				discoverFields(childNode, fullPath, fields, depth + 1);
			}
		}
	}

	private List<JsonNode> findSampleItems(JsonNode rootNode, String itemArrayField) {
		JsonNode itemNode = rootNode;

		if (itemArrayField != null & !itemArrayField.isBlank()) {
			itemNode = rootNode.get(itemArrayField);
		}

		if (itemNode == null || itemNode.isNull()) {
			return List.of();
		}

		if (itemNode.isArray()) {
			List<JsonNode> items = new ArrayList<>();
			Iterator<JsonNode> iterator = itemNode.elements();

			while (iterator.hasNext() && items.size() < MAX_SAMPLE_ITEMS) {
				items.add(iterator.next());
			}

			return items;
		}

		return List.of(itemNode);
	}

	private String findItemArrayField(JsonNode rootNode, String preferredItemArrayField) {
		if (rootNode == null || rootNode.isNull() || rootNode.isArray()) {
			return null;
		}

		if (preferredItemArrayField != null && !preferredItemArrayField.isBlank()) {
			JsonNode preferredNode = rootNode.get(preferredItemArrayField);

			if (preferredNode != null && preferredNode.isArray()) {
				return preferredItemArrayField;
			}
		}

		for (String field : ITEM_ARRAY_FIELDS) {
			JsonNode possibleArray = rootNode.get(field);

			if (possibleArray != null && possibleArray.isArray()) {
				return field;
			}

		}
		return null;
	}
}
