package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiSourceProfileValidationResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.CreateApiSourceProfileRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.RunApiSourceProfileRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSampleReader;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.ApiSourceProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSourceProfileServiceTest {

	private static final String API_URL = "http://localhost:9000/sample-api-symbols.json";

	@Mock
	private ApiSourceProfileRepository repository;

	@Mock
	private SourceSampleReader sourceSampleReader;

	@Mock
	private SourceMappingValidator sourceMappingValidator;

	@Mock
	private ApiSymbolIngestionService apiSymbolIngestionService;

	private ObjectMapper objectMapper;
	private ApiSourceProfileService service;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		service = new ApiSourceProfileService(repository, sourceSampleReader, sourceMappingValidator, apiSymbolIngestionService);
	}

	@Test
	void shouldCreateValidatedDraftProfile() throws Exception {
		SourceFieldMapping mapping = mapping();

		CreateApiSourceProfileRequest request = new CreateApiSourceProfileRequest("Local Egyptian API", "Local mock symbol API", API_URL, mapping);

		JsonNode sampleItem = objectMapper.readTree("""
				{
				  "symbolCode": "A1",
				  "imageUrl": "https://example.org/a1.png"
				}
				""");

		SourceSample sample = new SourceSample("symbols", List.of(sampleItem));

		when(repository.existsByProfileNameIgnoreCase("Local Egyptian API")).thenReturn(false);

		when(sourceSampleReader.readSample(API_URL,"symbols")).thenReturn(sample);

		when(sourceMappingValidator.validate(mapping, sample.sampleItems())).thenReturn(new SourceMappingValidationResult(true, List.of(), List.of("Optional place field was not mapped")));

		when(repository.save(any(ApiSourceProfile.class)))
				.thenAnswer(invocation -> {
					ApiSourceProfile profile =
							invocation.getArgument(0);

					profile.setId(7L);

					return profile;
				});

		ApiSourceProfileValidationResponse response = service.createDraft(request);

		assertThat(response.profile().id()).isEqualTo(7L);
		assertThat(response.profile().status()).isEqualTo(ApiSourceProfileStatus.DRAFT);
		assertThat(response.profile().sourceFieldMapping()).isEqualTo(mapping);
		assertThat(response.warnings()).containsExactly("Optional place field was not mapped");
	}

	@Test
	void shouldApproveProfileAfterRevalidation() throws Exception {
		ApiSourceProfile profile = profile(ApiSourceProfileStatus.DRAFT);

		JsonNode sampleItem = objectMapper.readTree("""
				{
				  "symbolCode": "A1",
				  "imageUrl": "https://example.org/a1.png"
				}
				""");

		SourceSample sample = new SourceSample("symbols", List.of(sampleItem));

		when(repository.findById(4L)).thenReturn(Optional.of(profile));
		when(sourceSampleReader.readSample(API_URL, "symbols")).thenReturn(sample);
		when(sourceMappingValidator.validate(mapping(), sample.sampleItems())).thenReturn(
				new SourceMappingValidationResult(true, List.of(), List.of()));

		when(repository.save(profile)).thenReturn(profile);
		ApiSourceProfileValidationResponse response = service.approve(4L);
		assertThat(response.profile().status()).isEqualTo(ApiSourceProfileStatus.APPROVED);
		assertThat(response.profile().approvedAt()).isNotNull();
	}

	@Test
	void shouldRunApprovedProfileUsingStoredMapping() {
		ApiSourceProfile profile = profile(ApiSourceProfileStatus.APPROVED);

		ApiIngestionResultResponse expectedResult = new ApiIngestionResultResponse(11L, "API", "Local mock symbol API", API_URL, IngestionStatus.COMPLETED, 2, 0, 0, List.of(21L, 22L), List.of());

		when(repository.findById(4L)).thenReturn(Optional.of(profile));
		when(apiSymbolIngestionService.ingestApi(any(ApiIngestionRequest.class))).thenReturn(expectedResult);

		ApiIngestionResultResponse result = service.run(4L, new RunApiSourceProfileRequest(1L));
		ArgumentCaptor<ApiIngestionRequest> requestCaptor = ArgumentCaptor.forClass(ApiIngestionRequest.class);

		verify(apiSymbolIngestionService).ingestApi(requestCaptor.capture());

		ApiIngestionRequest capturedRequest = requestCaptor.getValue();

		assertThat(capturedRequest.languageId()).isEqualTo(1L);
		assertThat(capturedRequest.sourceName()).isEqualTo("Local mock symbol API");
		assertThat(capturedRequest.apiUrl()).isEqualTo(API_URL);
		assertThat(capturedRequest.sourceFieldMapping()).isEqualTo(mapping());
		assertThat(result).isEqualTo(expectedResult);
	}

	@Test
	void shouldRejectRunningUnapprovedProfile() {
		ApiSourceProfile profile = profile(ApiSourceProfileStatus.DRAFT);

		when(repository.findById(4L)).thenReturn(Optional.of(profile));

		assertThatThrownBy(() ->
				service.run(
						4L,
						new RunApiSourceProfileRequest(1L)
				)
		)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(
						"API source profile must be approved before it can run"
				);
	}

	private SourceFieldMapping mapping() {
		return new SourceFieldMapping(
				"symbols",
				"symbolCode",
				"imageUrl",
				"label",
				null,
				null,
				null,
				null,
				null
		);
	}

	private ApiSourceProfile profile(ApiSourceProfileStatus status) {
		SourceFieldMapping mapping = mapping();

		return ApiSourceProfile.builder()
				.id(4L)
				.profileName("Local Egyptian API")
				.sourceName("Local mock symbol API")
				.apiUrl(API_URL)
				.status(status)
				.itemArrayField(mapping.itemArrayField())
				.symbolCodeField(mapping.symbolCodeField())
				.imagePathField(mapping.imagePathField())
				.titleField(mapping.titleField())
				.descriptionField(mapping.descriptionField())
				.placeField(mapping.placeField())
				.periodField(mapping.periodField())
				.dateStartField(mapping.dateStartField())
				.dateEndField(mapping.dateEndField())
				.build();
	}
}