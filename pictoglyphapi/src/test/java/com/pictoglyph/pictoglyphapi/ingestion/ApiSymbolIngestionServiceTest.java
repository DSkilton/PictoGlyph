package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSymbolIngestionServiceTest {

	private static final String API_URL = "http://localhost:9000/sample-api-symbols.json";

	@Mock
	private LanguageRepository languageRepository;
	@Mock
	private SymbolRepository symbolRepository;
	@Mock
	private IngestionJobRepository ingestionJobRepository;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private RemoteImageStorageService remoteImageStorageService;
	@Mock
	private SourceMappingValidator sourceMappingValidator;
	private ApiSymbolIngestionService service;

	@BeforeEach
	void setUp() {
		service = new ApiSymbolIngestionService(languageRepository, symbolRepository, ingestionJobRepository, restTemplate, new ObjectMapper(), remoteImageStorageService, sourceMappingValidator, new SourceFieldValueReader());
	}

	@Test
	void shouldTreatNullItemArrayFieldAsSingleRootItem() {
		SourceFieldMapping mapping = new SourceFieldMapping(null, "symbolCode", "imageUrl",  "label",null, null, null, null, null);

		ApiIngestionRequest request = new ApiIngestionRequest(
				1L,
				"Single item API",
				API_URL,
				mapping
		);

		String responseJson = """
				{
				  "symbolCode": "A1",
				  "imageUrl": "https://example.org/images/a1.png",
				  "label": "Seated man"
				}
				""";

		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.build();

		stubIngestionJobRepository();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
		when(restTemplate.getForObject(API_URL, String.class)).thenReturn(responseJson);
		when(sourceMappingValidator.validate(eq(mapping), anyList()))
				.thenReturn(
						new SourceMappingValidationResult(
								true,
								List.of(),
								List.of()
						)
				);

		when(symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(
				1L,
				"A1"
		)).thenReturn(false);

		when(remoteImageStorageService.downloadedImage(
				"https://example.org/images/a1.png",
				"API",
				1L,
				"A1"
		)).thenReturn(
				new DownloadedImage(
						"https://example.org/images/a1.png",
						"C:\\pictoglyph\\A1.png"
				)
		);

		when(symbolRepository.save(any(Symbol.class)))
				.thenAnswer(invocation -> {
					Symbol symbol = invocation.getArgument(0);
					symbol.setId(10L);
					return symbol;
				});

		ApiIngestionResultResponse result = service.ingestApi(request);

		assertThat(result.status()).isEqualTo(IngestionStatus.COMPLETED);

		assertThat(result.importedCount()).isEqualTo(1);
		assertThat(result.skippedCount()).isZero();
		assertThat(result.manualProcessingCount()).isZero();

		assertThat(result.createdSymbolIds()).containsExactly(10L);
	}

	@Test
	void shouldReportMissingItemArrayPathRatherThanThrowNullPointerException() {
		SourceFieldMapping mapping = new SourceFieldMapping("missing.items", "symbolCode", "imageUrl", null, null, null, null, null, null);

		ApiIngestionRequest request = new ApiIngestionRequest(
				1L,
				"Missing array API",
				API_URL,
				mapping
		);

		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.build();

		stubIngestionJobRepository();

		when(languageRepository.findById(1L))
				.thenReturn(Optional.of(language));

		when(restTemplate.getForObject(API_URL, String.class))
				.thenReturn("""
						{
						  "symbols": []
						}
						""");

		assertThatThrownBy(() -> service.ingestApi(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(
						"API ingestion failed for: " + API_URL
				)
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage(
						"Item array field was not found: missing.items"
				);
	}

	private void stubIngestionJobRepository() {
		when(ingestionJobRepository.save(any(IngestionJob.class)))
				.thenAnswer(invocation -> {
					IngestionJob job = invocation.getArgument(0);

					if (job.getId() == null) {
						job.setId(100L);
					}

					return job;
				});
	}
}