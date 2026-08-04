package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiSourceProfileResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiSourceProfileValidationResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.CreateApiSourceProfileRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.RunApiSourceProfileRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSampleReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSchemaComparison;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSchemaDriftDetector;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.ApiSourceProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ApiSourceProfileService {
	private final ApiSourceProfileRepository apiSourceProfileRepository;
	private final SourceSampleReader sourceSampleReader;
	private final SourceMappingValidator sourceMappingValidator;
	private final ApiSymbolIngestionService apiSymbolIngestionService;
	private final SourceFieldDiscoveryService sourceFieldDiscoveryService;
	private final SourceSchemaDriftDetector sourceSchemaDriftDetector;

	@Transactional
	public ApiSourceProfileValidationResponse createDraft(CreateApiSourceProfileRequest request) {
		String profileName = request.profileName().trim();

		if (apiSourceProfileRepository.existsByProfileNameIgnoreCase(profileName)) {
			throw new IllegalArgumentException("An API source profile already exists with the name: " + profileName);
		}

		SourceFieldMapping mapping = cleanMapping(request.sourceFieldMapping());
		SourceMappingValidationResult validationResult = validateMapping(request.apiUrl(), mapping);

		requireValidMapping(validationResult);

		ApiSourceProfile profile = ApiSourceProfile.builder()
				.profileName(profileName)
				.sourceName(request.sourceName().trim())
				.apiUrl(request.apiUrl().trim())
				.status(ApiSourceProfileStatus.DRAFT)
				.itemArrayField(mapping.itemArrayField())
				.symbolCodeField(mapping.symbolCodeField())
				.imagePathField(mapping.imagePathField())
				.titleField(mapping.titleField())
				.descriptionField(mapping.descriptionField())
				.placeField(mapping.placeField())
				.dateStartField(mapping.dateStartField())
				.dateEndField(mapping.dateEndField())
				.validatedAt(LocalDateTime.now())
				.build();

		ApiSourceProfile savedProfile = apiSourceProfileRepository.save(profile);

		return new ApiSourceProfileValidationResponse(toResponse(savedProfile), validationResult.warnings());
	}

	@Transactional
	public ApiSourceProfileValidationResponse approve(Long profileId) {
		ApiSourceProfile profile = findEntity(profileId);
		SourceFieldMapping mapping = toMapping(profile);

		SourceSample sample = sourceSampleReader.readSample(profile.getApiUrl(), mapping.itemArrayField());
		SourceMappingValidationResult validationResult = validateMapping(profile.getApiUrl(), mapping);

		requireValidMapping(validationResult);

		Set<String> discoveredFields = sourceFieldDiscoveryService.discoverFields(sample.sampleItems());
		LocalDateTime now = LocalDateTime.now();

		profile.setStatus(ApiSourceProfileStatus.APPROVED);
		profile.setValidatedAt(now);
		profile.setApprovedAt(now);
		profile.setApprovedSchemaFields(createSchemaSnapshot(discoveredFields));

		ApiSourceProfile savedProfile = apiSourceProfileRepository.save(profile);

		return new ApiSourceProfileValidationResponse(toResponse(savedProfile), validationResult.warnings());
	}

	private JsonNode createSchemaSnapshot(Set<String> discoveredFields) {
		ArrayNode snapshot = JsonNodeFactory.instance.arrayNode();

		if (discoveredFields == null) {
			return snapshot;
		}

		new TreeSet<>(discoveredFields).stream()
				.filter(field -> field != null && !field.isBlank())
				.map(String::trim)
				.forEach(snapshot::add);

		return snapshot;
	}

	@Transactional(readOnly = true)
	public List<ApiSourceProfileResponse> findAll() {
		return apiSourceProfileRepository
				.findAllByOrderByCreatedAtDesc()
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public ApiSourceProfileResponse findById(Long profileId) {
		return toResponse(findEntity(profileId));
	}

	@Transactional
	public ApiIngestionResultResponse run(Long profileId, RunApiSourceProfileRequest request) {
		ApiSourceProfile profile = findEntity(profileId);

		if (profile.getStatus() != ApiSourceProfileStatus.APPROVED) {
			throw new IllegalStateException("API source profile must be approved before it can run");
		}

		SourceFieldMapping mapping = toMapping(profile);
		checkForBreakingSchemaDrift(profile, mapping);

		ApiIngestionRequest ingestionRequest = new ApiIngestionRequest(request.languageId(), profile.getSourceName(), profile.getApiUrl(), mapping);

		return apiSymbolIngestionService.ingestApi(ingestionRequest);
	}

	private void checkForBreakingSchemaDrift(ApiSourceProfile profile, SourceFieldMapping mapping) {
		List<String> approvedFields = readSchemaSnapshot(profile);

		if (approvedFields.isEmpty()) {
			throw new IllegalStateException("API source profile does not have an approved schema snapshot. Approve the profile again before running it.");
		}

		SourceSample currentSample = sourceSampleReader.readSample(profile.getApiUrl(), mapping.itemArrayField());

		Set<String> currentFields = sourceFieldDiscoveryService.discoverFields(currentSample.sampleItems());

		SourceSchemaComparison comparison = sourceSchemaDriftDetector.comparison(new HashSet<>(approvedFields), currentFields);

		if (comparison.hasBreakingChanges()) {
			throw new IllegalStateException("Breaking API schema drift detected. "
					+ "The following approved fields are no longer present: "
					+ String.join(", ", comparison.removedFields())
					+ ". Review and approve the source profile again "
					+ "before running it.");
		}
	}

	private ApiSourceProfile findEntity(Long profileId) {
		return apiSourceProfileRepository.findById(profileId).orElseThrow(() -> new IllegalArgumentException("No API source profile found for id: " + profileId));

	}

	private ApiSourceProfileResponse toResponse(ApiSourceProfile profile) {
		return new ApiSourceProfileResponse(
				profile.getId(),
				profile.getProfileName(),
				profile.getSourceName(),
				profile.getApiUrl(),
				profile.getStatus(),
				toMapping(profile),
				readSchemaSnapshot(profile),
				profile.getCreatedAt(),
				profile.getUpdatedAt(),
				profile.getValidatedAt(),
				profile.getApprovedAt()
		);
	}

	private List<String> readSchemaSnapshot(ApiSourceProfile profile) {
		JsonNode snapshot = profile.getApprovedSchemaFields();

		if (snapshot == null || !snapshot.isArray()) {
			return List.of();
		}

		List<String> fields = new ArrayList<>();

		for (JsonNode fieldNode : snapshot) {
			if (!fieldNode.isTextual()) {
				continue;
			}

			String field = fieldNode.asText().trim();

			if (!field.isBlank()) {
				fields.add(field);
			}
		}

		return List.copyOf(fields);
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

	private void requireValidMapping(SourceMappingValidationResult validationResult) {
		if (!validationResult.valid()) {
			throw new IllegalArgumentException("Invalid source field mapping: " + String.join("; ", validationResult.errors()));
		}

	}

	private SourceMappingValidationResult validateMapping(String apiUrl, SourceFieldMapping mapping) {
		SourceSample sample = sourceSampleReader.readSample(apiUrl, mapping.itemArrayField());

		return sourceMappingValidator.validate(mapping, sample.sampleItems());
	}

	private SourceFieldMapping cleanMapping(SourceFieldMapping mapping) {
		if (mapping == null) {
			throw new IllegalArgumentException("Source field mapping is required");
		}

		return new SourceFieldMapping(
				cleanPath(mapping.itemArrayField()),
				cleanPath(mapping.symbolCodeField()),
				cleanPath(mapping.imagePathField()),
				cleanPath(mapping.titleField()),
				cleanPath(mapping.descriptionField()),
				cleanPath(mapping.placeField()),
				cleanPath(mapping.periodField()),
				cleanPath(mapping.dateStartField()),
				cleanPath(mapping.dateEndField())
		);
	}

	private String cleanPath(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}
}
