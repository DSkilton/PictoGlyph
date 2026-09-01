package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.assertj.core.api.Assertions;

import static com.pictoglyph.pictoglyphapi.TestConstants.ANCIENT_SCRIPTS_PILOT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasetSourceQueueServiceTest {

	public static final String API = "API";
	public static final String MAYA_API_UNAVAILABLE = "Maya API unavailable";
	@Mock
	private DatasetPreparationService datasetPreparationService;

	@Mock
	private ApiSymbolIngestionService apiSymbolIngestionService;

	private DatasetSourceQueueService service;

	private ApiIngestionRequest MAYA = createRequest("Maya source", "https://example.org/may");
	private ApiIngestionRequest EGYPTIAN = createRequest("Egyptian source", "https://example.org/egyptian");
	private ApiIngestionRequest CUNEIFORM = createRequest("Cuneiform source", "https://example.org/cuneiform");
	DatasetSourceQueueRequest request = new DatasetSourceQueueRequest("Ancient scripts pilot", List.of(MAYA, EGYPTIAN));

	@BeforeEach
	void setUp() {
		service = new DatasetSourceQueueService(datasetPreparationService, apiSymbolIngestionService);
	}

	@Test
	void shouldProcessEverySourceInQueue() {
		DatasetPreparationResponse createdDataset = preparation(DatasetReadinessStatus.INGESTING);
		DatasetPreparationResponse completedDataset = preparation(DatasetReadinessStatus.READY_FOR_ML);

		when(datasetPreparationService.create("Ancient scripts pilot")).thenReturn(createdDataset);

		ApiIngestionResultResponse mayaResult = successfulResult(100L, MAYA, 10L);
		ApiIngestionResultResponse egyptianResult = successfulResult(101L, EGYPTIAN, 11L);

		when(apiSymbolIngestionService.ingestApi(MAYA)).thenReturn(mayaResult);
		when(apiSymbolIngestionService.ingestApi(EGYPTIAN)).thenReturn(egyptianResult);
		when(datasetPreparationService.completeIngestion(1L)).thenReturn(completedDataset);

		DatasetSourceQueueResponse result = service.run(request);

		assertThat(result.sourceCount()).isEqualTo(2);
		assertThat(result.completedSourceCount()).isEqualTo(2);
		assertThat(result.failedSourceCount()).isZero();
		assertThat(result.sources()).hasSize(2);

		verify(datasetPreparationService).recordIngestionResult(1L, mayaResult);
		verify(datasetPreparationService).recordIngestionResult(1L, egyptianResult);
		verify(datasetPreparationService).completeIngestion(1L);

	}

	@Test
	void shouldContinueWhenSourceRequiresHumanReview() {
		when(datasetPreparationService.create("Ancient scripts pilot")).thenReturn(preparation(DatasetReadinessStatus.INGESTING));

		ApiIngestionResultResponse mayaResult = new ApiIngestionResultResponse(100L, API, MAYA.sourceName(), MAYA.apiUrl(), IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING, 20, 0, 3, List.of(10L, 11L), List.of());
		ApiIngestionResultResponse egyptianResult = successfulResult(101L, EGYPTIAN, 12L);

		when(apiSymbolIngestionService.ingestApi(MAYA)).thenReturn(mayaResult);
		when(apiSymbolIngestionService.ingestApi(EGYPTIAN)).thenReturn(egyptianResult);

		when(datasetPreparationService.completeIngestion(1L)).thenReturn(preparation(DatasetReadinessStatus.REVIEW_REQUIRED));

		DatasetSourceQueueResponse result = service.run(request);

		assertThat(result.sourceCount()).isEqualTo(2);
		assertThat(result.completedSourceCount()).isEqualTo(2);
		assertThat(result.completedSourceCount()).isEqualTo(2);
		assertThat(result.failedSourceCount()).isZero();
		assertThat(result.sources().get(0).ingestionStatus()).isEqualTo(IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING);
		assertThat(result.sources().get(1).sourceName()).isEqualTo("Egyptian source");
		assertThat(result.dataset().status()).isEqualTo(DatasetReadinessStatus.REVIEW_REQUIRED);
		verify(apiSymbolIngestionService).ingestApi(EGYPTIAN);
	}

	@Test
	void shouldContinueWhenSourceFails() {
		DatasetSourceQueueRequest request = new DatasetSourceQueueRequest(ANCIENT_SCRIPTS_PILOT, List.of(MAYA, EGYPTIAN, CUNEIFORM));

		when(datasetPreparationService.create(ANCIENT_SCRIPTS_PILOT)).thenReturn(preparation(DatasetReadinessStatus.INGESTING));
		when(apiSymbolIngestionService.ingestApi(MAYA)).thenThrow(new IllegalStateException(MAYA_API_UNAVAILABLE));

		ApiIngestionResultResponse egyptianResult = successfulResult(101L, EGYPTIAN, 20L);
		ApiIngestionResultResponse cuneiformResult = successfulResult(102L, CUNEIFORM, 30L);

		when(apiSymbolIngestionService.ingestApi(EGYPTIAN)).thenReturn(egyptianResult);
		when(apiSymbolIngestionService.ingestApi(CUNEIFORM)).thenReturn(cuneiformResult);
		when(datasetPreparationService.completeIngestion(1L)).thenReturn(preparation(DatasetReadinessStatus.RETRY_REQUIRED));

		DatasetSourceQueueResponse result = service.run(request);

		assertThat(result.sourceCount()).isEqualTo(2);
		assertThat(result.completedSourceCount()).isEqualTo(3);
		assertThat(result.failedSourceCount()).isEqualTo(1);
		assertThat(result.sources().get(0).ingestionStatus()).isEqualTo(IngestionStatus.FAILED);

		assertThat(result.sources().get(0).errorMessage()).contains(MAYA_API_UNAVAILABLE);
		assertThat(result.sources().get(1).sourceName()).isEqualTo("Egyptian source");
		assertThat(result.sources().get(2).sourceName()).isEqualTo("Cuneiform source");

		verify(datasetPreparationService).recordSourceFailure(1L, API, "Maya source", "https://example.org/maya", MAYA_API_UNAVAILABLE);
		verify(apiSymbolIngestionService).ingestApi(EGYPTIAN);
		verify(apiSymbolIngestionService).ingestApi(CUNEIFORM);

		assertThat(result.dataset().status()).isEqualTo(DatasetReadinessStatus.RETRY_REQUIRED);
	}

	@Test
	void shouldCompleteDatasetOnlyAfterEverySourceAttemtped() {
		when(datasetPreparationService.create(ANCIENT_SCRIPTS_PILOT)).thenReturn(preparation(DatasetReadinessStatus.INGESTING));

		ApiIngestionResultResponse mayaResult = successfulResult(100L, MAYA, 10L);
		ApiIngestionResultResponse egyptianResult = successfulResult(101L, EGYPTIAN, 20L);

		when(apiSymbolIngestionService.ingestApi(MAYA)).thenReturn(mayaResult);
		when(apiSymbolIngestionService.ingestApi(EGYPTIAN)).thenReturn(egyptianResult);
		when(datasetPreparationService.completeIngestion(1L)).thenReturn(preparation(DatasetReadinessStatus.READY_FOR_ML));

		service.run(new DatasetSourceQueueRequest(ANCIENT_SCRIPTS_PILOT, List.of(MAYA, EGYPTIAN)));

		InOrder order = inOrder(apiSymbolIngestionService, datasetPreparationService);
		order.verify(apiSymbolIngestionService).ingestApi(MAYA);
		order.verify(datasetPreparationService).recordIngestionResult(1L, mayaResult);
		order.verify(apiSymbolIngestionService).ingestApi(EGYPTIAN);
		order.verify(datasetPreparationService).recordIngestionResult(1l, egyptianResult);
		order.verify(datasetPreparationService).completeIngestion(1L);
	}

	@Test
	void shouldRejectEmptySourceQueue() {
		DatasetSourceQueueRequest request = new DatasetSourceQueueRequest(ANCIENT_SCRIPTS_PILOT, List.of());

		Assertions.assertThatThrownBy(
						() -> service.run(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("At least one ingestion source is required");

		verify(datasetPreparationService, never()).create(any());
	}

	private ApiIngestionRequest createRequest(String sourceName, String apiUrl) {
		SourceFieldMapping mapping = new SourceFieldMapping("symbols", "symbolCode", "imageUrl", "label", null, null, null, null, null);

		return new ApiIngestionRequest(1l, sourceName, apiUrl, mapping);
	}

	private ApiIngestionResultResponse successfulResult(Long jobId, ApiIngestionRequest request, Long symbolId) {
		return new ApiIngestionResultResponse(jobId, API, request.sourceName(), request.apiUrl(), IngestionStatus.COMPLETED, 1, 0, 0, List.of(symbolId), List.of());
	}

	private DatasetPreparationResponse preparation(DatasetReadinessStatus status) {
		return new DatasetPreparationResponse(1L, "Ancient scripts pilot", status, null, null, null, null, null, null, null, null);
	}
}