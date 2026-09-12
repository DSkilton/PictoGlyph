package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.dataset.DatasetPreparationService;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import com.pictoglyph.pictoglyphapi.ingestion.api.ReprocessIngestionReviewItemRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ReprocessIngestionReviewItemResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionReviewReprocessingServiceTest {

	public static final String PATH = "http://localhost:9000/maya.json";
	public static final String SYMBOL_CODE = "symbolCode";
	public static final String IMAGE_URL = "imageUrl";
	public static final String MAYA_001 = "MAYA001";
	@Mock
	private IngestionReviewItemRepository reviewItemRepository;

	@Mock
	private ApiSymbolIngestionService apiSymbolIngestionService;

	@Mock
	private DatasetPreparationService datasetPreparationService;

	private IngestionReviewReprocessingService service;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		service = new IngestionReviewReprocessingService(reviewItemRepository, apiSymbolIngestionService, datasetPreparationService);
	}

	@Test
	void shouldReprocessCorrectedItemAndResolveReview() {
		IngestionJob ingestionJob = IngestionJob.builder()
						.id(100L)
						.sourcePath(PATH)
						.build();

		ObjectNode original = objectMapper.createObjectNode();
		original.put(SYMBOL_CODE, "");
		original.put(IMAGE_URL, PATH);

		IngestionReviewItem reviewItem = IngestionReviewItem.builder()
						.id(55L)
						.ingestionJob(ingestionJob)
						.itemIndex(0)
						.reason("Missing symbol code")
						.rawItem(original)
						.status(IngestionReviewStatus.PENDING)
						.build();

		when(reviewItemRepository.findById(55L)).thenReturn(Optional.of(reviewItem));

		ObjectNode corrected = original.deepCopy();
		corrected.put(SYMBOL_CODE, MAYA_001);

		SourceFieldMapping mapping = new SourceFieldMapping(
						"symbols",
						SYMBOL_CODE,
						IMAGE_URL,
						"label",
						null,
						null,
						null,
						null,
						null
				);

		ReprocessIngestionReviewItemRequest request = new ReprocessIngestionReviewItemRequest(1L, "Maya source", mapping, corrected, "Added missing symbol code");

		Symbol savedSymbol = Symbol.builder()
						.id(77L)
						.symbolCode(MAYA_001)
						.build();

		when(apiSymbolIngestionService.reprocessReviewedItem(1L, "Maya source", PATH, mapping, corrected)).thenReturn(savedSymbol);
		when(reviewItemRepository.saveAndFlush(reviewItem)).thenReturn(reviewItem);

		ReprocessIngestionReviewItemResponse response = service.reprocess(55L, request);

		assertThat(response.status()).isEqualTo(IngestionReviewStatus.RESOLVED);
		assertThat(response.symbolId()).isEqualTo(77L);
		assertThat(response.correctedItem().path(SYMBOL_CODE).asText()).isEqualTo(MAYA_001);
		assertThat(reviewItem.getReprocessedAt()).isNotNull();
		assertThat(reviewItem.getReprocessedSymbolId()).isEqualTo(77L);
		verify(datasetPreparationService).recordReviewedSymbolForIngestionJob(100L, 77L);
		verify(datasetPreparationService).revalidateForIngestionJob(100L);
	}
}