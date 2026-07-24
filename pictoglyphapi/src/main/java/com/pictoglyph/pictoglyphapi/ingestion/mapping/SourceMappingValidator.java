package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SourceMappingValidator {
	private static final double LOW_COVERAGE_THRESHOLD = 0.50;
	private final SourceFieldValueReader sourceFieldValueReader;

	public SourceMappingValidationResult validate(SourceFieldMapping mapping, List<JsonNode> candidatItems) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		if (mapping == null) {
			errors.add("Source field mapping is required");

			return new SourceMappingValidationResult(false, errors, warnings);
		}

		validateRequiredMapping("symbolCodeField", mapping.symbolCodeField(), errors);
		validateRequiredMapping("imagePathField", mapping.imagePathField(), errors);
		validateDifferentRequiredFields(mapping, errors);

		List<JsonNode> safeCandidateItems = candidatItems == null ? List.of() : candidatItems;
		
		if (safeCandidateItems.isEmpty()) {
			errors.add("No candidate items were found using the supplied mapping");
			
			return new SourceMappingValidationResult(false, errors, warnings);
		}
		
		Map<String, String> mappedFields = mappedFields(mapping);

		for (Map.Entry<String, String> entry : mappedFields.entrySet()) {
			validateFieldCoverage(entry.getKey(), entry.getValue(), safeCandidateItems, isRequiredField(entry.getKey()), errors, warnings);
		}

		validateDuplicateMappings(mappedFields, warnings);

		return new SourceMappingValidationResult(errors.isEmpty(), errors, warnings);
	}

	private void validateDuplicateMappings(Map<String, String> mappedFields, List<String> warnings) {
		Map<String, List<String>> targetsBySourceField = new LinkedHashMap<>();

		for (Map.Entry<String, String> entry : mappedFields.entrySet()) {
			String sourceField = entry.getValue();

			if (sourceField == null || sourceField.isBlank()) {
				continue;
			}

			targetsBySourceField
					.computeIfAbsent(sourceField, ignored -> new ArrayList<>())
					.add(entry.getKey());
		}

		for (Map.Entry<String, List<String>> entry : targetsBySourceField.entrySet()) {

			if (entry.getValue().size() > 1) {
				warnings.add("Source field " + entry.getKey() + " is mapped to multiple targets: " + String.join(", ", entry.getValue()));
			}
		}
	}

	private boolean isRequiredField(String targetField) {
		return targetField.equals("symbolCodeField") || targetField.equals("imagePathField");

	}

	private void validateFieldCoverage(String targetField, String sourceField, List<JsonNode> candidateItems, boolean required, List<String> errors, List<String> warnings) {
		if (sourceField == null || sourceField.isBlank()) {
			return;
		}

		long populatedCount = candidateItems.stream()
				.map(item -> sourceFieldValueReader.readText(item, sourceField))
				.filter(value -> value != null && !value.isBlank())
				.count();

		if (populatedCount == 0) {
			String message = targetField + " maps to " + sourceField + ", but no sample records contain a usable value";

			if (required) {
				errors.add(message);
			} else {
				warnings.add(message);
			}
			return;
		}

		double coverage = (double) populatedCount / candidateItems.size();

		if (coverage < LOW_COVERAGE_THRESHOLD) {
			long coveragePercentage = Math.round(coverage * 100);
			warnings.add(targetField + " maps to " + sourceField + ", but only " + coveragePercentage + "% of sampled records contain a usable value");
		}
	}

	private Map<String, String> mappedFields(SourceFieldMapping mapping) {

		Map<String, String> fields = new LinkedHashMap<>();

		fields.put("symbolCodeField", mapping.symbolCodeField());
		fields.put("imagePathField", mapping.imagePathField());
		fields.put("titleField", mapping.titleField());
		fields.put("descriptionField", mapping.descriptionField());
		fields.put("placeField", mapping.placeField());
		fields.put("periodField", mapping.periodField());
		fields.put("dateStartField", mapping.dateStartField());
		fields.put("dateEndField", mapping.dateEndField());

		return fields;
	}

	private void validateDifferentRequiredFields(SourceFieldMapping mapping, List<String> errors) {

		if (mapping.symbolCodeField() == null || mapping.imagePathField() == null) {
			return;
		}

		if (mapping.symbolCodeField().equals(mapping.imagePathField())) {
			errors.add("symbolCodeField and imagePathField cannot use the same source field");
		}
	}

	private void validateRequiredMapping(String targetField, String sourceField, List<String> errors) {

		if (sourceField == null || sourceField.isBlank()) {
			errors.add(targetField + " must be mapped");
		}
	}
}
