package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionJobRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSymbolIngestionServiceTest {

	private static final String API_URL = "http://localhost:9000/sample-api-symbols.json";
	private static final String IMAGE_CHECKSUM = "abc123checksum";

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

	@Mock
	private IngestionReviewItemRepository ingestionReviewItemRepository;

	@Mock
	private ImportedSymbolPersistenceService importedSymbolPersistenceService;

	@Mock
	private ImageChecksumService imageChecksumService;

	private ApiSymbolIngestionService service;

	@BeforeEach
	void setUp() {
		service = new ApiSymbolIngestionService(languageRepository, symbolRepository, ingestionJobRepository, restTemplate, new ObjectMapper(), remoteImageStorageService, sourceMappingValidator, new SourceFieldValueReader(), ingestionReviewItemRepository, importedSymbolPersistenceService, imageChecksumService);
	}

	@Test
	void shouldPersistMissingSymbolCodeForManualReview() {
		SourceFieldMapping mapping = new SourceFieldMapping("symbols", "symbolCode", "imageUrl", "label", null, null, null, null, null);
		ApiIngestionRequest request = new ApiIngestionRequest(1L, "Review source", API_URL, mapping);

		String responseJson = """
			{
			  "symbols": [
			    {
			      "symbolCode": "",
			      "imageUrl": "https://example.org/a1.png",
			      "label": "Missing code"
			    }
			  ]
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
		when(sourceMappingValidator.validate(eq(mapping), anyList())).thenReturn(new SourceMappingValidationResult(true, List.of(), List.of()));

		when(ingestionReviewItemRepository.save(any(IngestionReviewItem.class))).thenAnswer(invocation -> {
			IngestionReviewItem item = invocation.getArgument(0);
			item.setId(55L);
			item.setStatus(IngestionReviewStatus.PENDING);
			return item;
		});

		ApiIngestionResultResponse result = service.ingestApi(request);

		assertThat(result.status()).isEqualTo(IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING);
		assertThat(result.importedCount()).isZero();
		assertThat(result.manualProcessingCount()).isEqualTo(1);

		ArgumentCaptor<IngestionReviewItem> captor = ArgumentCaptor.forClass(IngestionReviewItem.class);
		verify(ingestionReviewItemRepository).save(captor.capture());

		IngestionReviewItem savedReviewItem = captor.getValue();

		assertThat(savedReviewItem.getIngestionJob().getId()).isEqualTo(100L);
		assertThat(savedReviewItem.getItemIndex()).isZero();
		assertThat(savedReviewItem.getReason()).isEqualTo("Missing symbol code");
		assertThat(savedReviewItem.getRawItem().path("label").asText()).isEqualTo("Missing code");

		verifyNoInteractions(imageChecksumService, importedSymbolPersistenceService);
	}

	@Test
	void shouldTreatNullItemArrayFieldAsSingleRootItem() {
		SourceFieldMapping mapping = new SourceFieldMapping(null, "symbolCode", "imageUrl", "label", null, null, null, null, null);
		ApiIngestionRequest request = new ApiIngestionRequest(1L, "Single item API", API_URL, mapping);

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

		DownloadedImage downloadedImage = new DownloadedImage("https://example.org/images/a1.png", "C:\\pictoglyph\\A1.png");

		stubIngestionJobRepository();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
		when(restTemplate.getForObject(API_URL, String.class)).thenReturn(responseJson);
		when(sourceMappingValidator.validate(eq(mapping), anyList())).thenReturn(new SourceMappingValidationResult(true, List.of(), List.of()));
		when(symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(1L, "A1")).thenReturn(false);
		when(remoteImageStorageService.downloadedImage("https://example.org/images/a1.png", "API", 1L, "A1")).thenReturn(downloadedImage);
		when(imageChecksumService.calculateSha256(downloadedImage.localPath())).thenReturn(IMAGE_CHECKSUM);

		stubImportedSymbolPersistence(10L);

		ApiIngestionResultResponse result = service.ingestApi(request);

		assertThat(result.status()).isEqualTo(IngestionStatus.COMPLETED);
		assertThat(result.importedCount()).isEqualTo(1);
		assertThat(result.skippedCount()).isZero();
		assertThat(result.manualProcessingCount()).isZero();
		assertThat(result.createdSymbolIds()).containsExactly(10L);

		verify(imageChecksumService).calculateSha256(downloadedImage.localPath());

		ArgumentCaptor<Symbol> symbolCaptor = ArgumentCaptor.forClass(Symbol.class);
		verify(importedSymbolPersistenceService).saveImportedSymbol(symbolCaptor.capture());

		Symbol savedSymbol = symbolCaptor.getValue();

		assertThat(savedSymbol.getSymbolCode()).isEqualTo("A1");
		assertThat(savedSymbol.getImagePath()).isEqualTo(downloadedImage.localPath());
		assertThat(savedSymbol.getMeta().path("imageChecksum").asText()).isEqualTo(IMAGE_CHECKSUM);
		assertThat(savedSymbol.getMeta().path("imageChecksumAlgorithm").asText()).isEqualTo("SHA-256");
		assertThat(savedSymbol.getMeta().path("originalImageUrl").asText()).isEqualTo(downloadedImage.originalUrl());
		assertThat(savedSymbol.getMeta().path("downloadedImagePath").asText()).isEqualTo(downloadedImage.localPath());
	}

	@Test
	void shouldRouteImageToManualReviewWhenChecksumFails() {
		SourceFieldMapping mapping = new SourceFieldMapping("symbols", "symbolCode", "imageUrl", "label", null, null, null, null, null);
		ApiIngestionRequest request = new ApiIngestionRequest(1L, "Checksum test source", API_URL, mapping);

		String responseJson = """
			{
			  "symbols": [
			    {
			      "symbolCode": "A1",
			      "imageUrl": "https://example.org/images/a1.png",
			      "label": "Seated man"
			    }
			  ]
			}
			""";

		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.build();

		DownloadedImage downloadedImage = new DownloadedImage("https://example.org/images/a1.png", "C:\\pictoglyph\\A1.png");

		stubIngestionJobRepository();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
		when(restTemplate.getForObject(API_URL, String.class)).thenReturn(responseJson);
		when(sourceMappingValidator.validate(eq(mapping), anyList())).thenReturn(new SourceMappingValidationResult(true, List.of(), List.of()));
		when(symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(1L, "A1")).thenReturn(false);
		when(remoteImageStorageService.downloadedImage("https://example.org/images/a1.png", "API", 1L, "A1")).thenReturn(downloadedImage);
		when(imageChecksumService.calculateSha256(downloadedImage.localPath())).thenThrow(new IllegalStateException("Could not calculate checksum"));

		ApiIngestionResultResponse result = service.ingestApi(request);

		assertThat(result.status()).isEqualTo(IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING);
		assertThat(result.importedCount()).isZero();
		assertThat(result.skippedCount()).isZero();
		assertThat(result.manualProcessingCount()).isEqualTo(1);
		assertThat(result.createdSymbolIds()).isEmpty();

		assertThat(result.manualProcessingItems()).singleElement().satisfies(item -> {
			assertThat(item.itemIndex()).isZero();
			assertThat(item.reason()).contains("Could not calculate checksum");
		});

		ArgumentCaptor<IngestionReviewItem> reviewCaptor = ArgumentCaptor.forClass(IngestionReviewItem.class);
		verify(ingestionReviewItemRepository).save(reviewCaptor.capture());

		IngestionReviewItem reviewItem = reviewCaptor.getValue();

		assertThat(reviewItem.getReason()).contains("Could not calculate checksum");
		assertThat(reviewItem.getRawItem().path("symbolCode").asText()).isEqualTo("A1");

		verifyNoInteractions(importedSymbolPersistenceService);
	}

	@Test
	void shouldReportMissingItemArrayPathRatherThanThrowNullPointerException() {
		SourceFieldMapping mapping = new SourceFieldMapping("missing.items", "symbolCode", "imageUrl", null, null, null, null, null, null);
		ApiIngestionRequest request = new ApiIngestionRequest(1L, "Missing array API", API_URL, mapping);

		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.scriptName("Egyptian hieroglyphs")
				.build();

		stubIngestionJobRepository();

		when(languageRepository.findById(1L)).thenReturn(Optional.of(language));
		when(restTemplate.getForObject(API_URL, String.class)).thenReturn("""
			{
			  "symbols": []
			}
			""");

		assertThatThrownBy(() -> service.ingestApi(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("API ingestion failed for: " + API_URL)
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("Item array field was not found: missing.items");

		verifyNoInteractions(imageChecksumService, importedSymbolPersistenceService);
	}

	private void stubIngestionJobRepository() {
		when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> {
			IngestionJob ingestionJob = invocation.getArgument(0);

			if (ingestionJob.getId() == null) {
				ingestionJob.setId(100L);
			}

			return ingestionJob;
		});
	}

	private void stubImportedSymbolPersistence(Long symbolId) {
		when(importedSymbolPersistenceService.saveImportedSymbol(any(Symbol.class))
		).thenAnswer(invocation -> {
			Symbol symbol = invocation.getArgument(0);
			symbol.setId(symbolId);

			return symbol;
		});
	}
}