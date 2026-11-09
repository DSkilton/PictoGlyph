package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSchemaDriftDetector;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.ApiSourceProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@Component
@Order(50)
@RequiredArgsConstructor
public class ApprovedProfileMappingStrategy implements SourceMappingStrategy {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApprovedProfileMappingStrategy.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private static final double REUSE_SCORE = 0.95;

	private final ApiSourceProfileRepository repository;
	private final SourceSchemaDriftDetector driftDetector;
	private final SourceMappingValidator validator;
	private final SourceFieldValueReader valueReader;

	@Override
	public String id() {
		return "APPROVED_PROFILE";
	}

	@Override
	public MappingStep apply(SourceMappingContext context, Map<SourceMappingTarget, FieldMatch> currentMatches) {

		// This strategy is intended to run first.
		// Never overwrite evidence from an earlier strategy.
		if (!currentMatches.isEmpty() || context.sample().sampleItems().isEmpty() || context.discoveredFields().isEmpty()) {
			return MappingStep.continueWith(currentMatches);
		}

		Optional<ApiSourceProfile> candidate = repository.findFirstBySourceNameIgnoreCaseAndApiUrlAndStatusOrderByApprovedAtDescIdDesc(context.sourceName().trim(), context.apiUrl().trim(), ApiSourceProfileStatus.APPROVED);

		if (candidate.isEmpty()) {
			return MappingStep.continueWith(currentMatches);
		}

		ApiSourceProfile profile = candidate.get();

		if (!Objects.equals(clean(profile.getItemArrayField()), clean(context.sample().itemArrayField()))) {
			return rejected(profile, "item_array_mismatch", currentMatches);
		}

		if (profile.getApprovedAt() == null) {
			return rejected(profile, "missing_approval_timestamp", currentMatches);
		}

		Set<String> approvedFields = readSnapshot(profile.getApprovedSchemaFields());

		if (approvedFields.isEmpty()) {
			return rejected(profile, "missing_schema_snapshot", currentMatches);
		}

		if (driftDetector.comparison(approvedFields, context.discoveredFields()).hasBreakingChanges()) {
			return rejected(profile, "schema_drift", currentMatches);
		}

		SourceFieldMapping mapping = toMapping(profile);

		SourceMappingValidationResult validation = validator.validate(mapping, context.sample().sampleItems());

		if (!validation.valid() || !validation.warnings().isEmpty()) {
			return rejected(profile, "mapping_validation_failed", currentMatches);
		}

		if (!requiredValuesPopulated(mapping, context)) {
			return rejected(profile, "incomplete_required_values", currentMatches);
		}

		Map<SourceMappingTarget, FieldMatch> matches = new EnumMap<>(SourceMappingTarget.class);

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			String fieldPath = mappedPath(mapping, target);

			if (fieldPath == null || fieldPath.isBlank()) {
				continue;
			}

			matches.put(target, new FieldMatch(target, fieldPath, REUSE_SCORE, "Reused approved source profile #" + profile.getId() + "; schema and sample values checked"));
		}

		LOGGER.info("Approved mapping profile reused: profileId={}, sampledItems={}", profile.getId(), context.sample().sampleItems().size());
		PROVENANCE_LOGGER.info("event=approved_mapping_reused profileId={} status=PROPOSED schemaChecked=true valuesChecked=true", profile.getId());

		return MappingStep.stopWith(matches);
	}

	private MappingStep rejected(ApiSourceProfile profile, String reason, Map<SourceMappingTarget, FieldMatch> currentMatches) {
		LOGGER.info("Approved profile not reusable: profileId={}, reason={}", profile.getId(), reason);
		PROVENANCE_LOGGER.info("event=approved_mapping_reuse_skipped profileId={} reason={}", profile.getId(), reason);

		return MappingStep.continueWith(currentMatches);
	}

	private boolean requiredValuesPopulated(SourceFieldMapping mapping, SourceMappingContext context) {
		for (JsonNode item : context.sample().sampleItems()) {
			if (valueReader.readText(item, mapping.imagePathField()) == null) {
				return false;
			}
		}

		return true;
	}

	private Set<String> readSnapshot(JsonNode snapshot) {
		if (snapshot == null || !snapshot.isArray()) {
			return Set.of();
		}

		Set<String> fields = new HashSet<>();

		for (JsonNode node : snapshot) {
			if (node.isTextual() && !node.asText().isBlank()) {
				fields.add(node.asText().trim());
			}
		}

		return fields;
	}

	private SourceFieldMapping toMapping(ApiSourceProfile profile) {
		return new SourceFieldMapping(
				profile.getItemArrayField(),
				profile.getSymbolCodeField(),
				profile.getImagePathField(),
				profile.getTitleField(),
				profile.getDescriptionField(),
				profile.getPlaceField(),
				profile.getPeriodField(),
				profile.getDateStartField(),
				profile.getDateEndField()
		);
	}

	private String mappedPath(SourceFieldMapping mapping, SourceMappingTarget target) {
		return switch (target) {
			case SYMBOL_CODE -> mapping.symbolCodeField();
			case IMAGE_PATH -> mapping.imagePathField();
			case TITLE -> mapping.titleField();
			case DESCRIPTION -> mapping.descriptionField();
			case PLACE -> mapping.placeField();
			case PERIOD -> mapping.periodField();
			case DATE_START -> mapping.dateStartField();
			case DATE_END -> mapping.dateEndField();
		};
	}

	private String clean(String value) {
		return value == null || value.isBlank()
				? null
				: value.trim();
	}
}
