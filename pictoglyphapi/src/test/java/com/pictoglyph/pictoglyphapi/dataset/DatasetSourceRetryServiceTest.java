package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationSourceResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryResponse;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceRetryAttempt;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.pictoglyph.pictoglyphapi.TestConstants.ANCIENT_SCRIPTS_PILOT;
import static com.pictoglyph.pictoglyphapi.TestConstants.EXAMPLE_ORG_MAYA;
import static com.pictoglyph.pictoglyphapi.TestConstants.MAYA_API_UNAVAILABLE;
import static com.pictoglyph.pictoglyphapi.TestConstants.MAYA_SOURCE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasetSourceRetryServiceTest {

	private static final Long DATASET_ID = 1L;
	private static final Long SOURCE_RESULT_ID = 20L;

	@Mock
	private DatasetPreparationService datasetPreparationService;

	@Mock
	private ApiSymbolIngestionService apiSymbolIngestionService;

	private DatasetSourceRetryService service;

	@BeforeEach
	void setUp() {
		service = new DatasetSourceRetryService(datasetPreparationService, apiSymbolIngestionService);
	}

	@Test
	void shouldRetryFailedSourceAndBecomeReadyForMl() {
		ApiIngestionRequest source = sourceRequest();
		DatasetPreparationSourceResponse failedSource = failedSource();

		when(datasetPreparationService.getRetryableSource(DATASET_ID, SOURCE_RESULT_ID)).thenReturn(failedSource);

		ApiIngestionResultResponse result = new ApiIngestionResultResponse(10L, "API", MAYA_SOURCE, EXAMPLE_ORG_MAYA, IngestionStatus.COMPLETED, 25, 0, 0, List.of(10L, 11L), List.of());

		when(apiSymbolIngestionService.ingestApi(source)).thenReturn(result);

		DatasetPreparationSourceRetryAttempt attempt = DatasetPreparationSourceRetryAttempt.builder()
				.attemptNumber(1)
				.ingestionJobId(10L)
				.ingestionStatus(IngestionStatus.COMPLETED)
				.importedCount(25)
				.skippedCount(0)
				.manualProcessingCount(0)
				.startedAt(LocalDateTime.now().minusSeconds(1))
				.completedAt(LocalDateTime.now())
				.build();

		when(datasetPreparationService.recordRetrySuccess(
				eq(DATASET_ID),
				eq(SOURCE_RESULT_ID),
				eq(result),
				any(LocalDateTime.class),
				any(LocalDateTime.class)
		)).thenReturn(attempt);

		when(datasetPreparationService.revalidate(DATASET_ID)).thenReturn(preparation(DatasetReadinessStatus.READY_FOR_ML));

		DatasetSourceRetryResponse response = service.retry(DATASET_ID, SOURCE_RESULT_ID, new DatasetSourceRetryRequest(source));
		assertThat(response.successful()).isTrue();
		assertThat(response.attemptNumber()).isEqualTo(1);
		assertThat(response.ingestionStatus()).isEqualTo(IngestionStatus.COMPLETED);
		assertThat(response.dataset().status()).isEqualTo(DatasetReadinessStatus.READY_FOR_ML);
	}

	@Test
	void shouldRemainRetryRetryRequiredWhenRetryFails() {
		ApiIngestionRequest source = sourceRequest();

		when(datasetPreparationService.getRetryableSource(DATASET_ID, SOURCE_RESULT_ID)).thenReturn(failedSource());
		when(apiSymbolIngestionService.ingestApi(source)).thenThrow(new IllegalStateException(MAYA_API_UNAVAILABLE));

		DatasetPreparationSourceRetryAttempt attempt = DatasetPreparationSourceRetryAttempt.builder()
				.attemptNumber(1)
				.ingestionStatus(IngestionStatus.FAILED)
				.errorMessage(MAYA_API_UNAVAILABLE)
				.importedCount(0)
				.skippedCount(0)
				.manualProcessingCount(0)
				.startedAt(LocalDateTime.now().minusSeconds(1))
				.completedAt(LocalDateTime.now())
				.build();

		when(datasetPreparationService.recordRetryFailure(
				eq(DATASET_ID),
				eq(SOURCE_RESULT_ID),
				eq(MAYA_API_UNAVAILABLE),
				any(LocalDateTime.class),
				any(LocalDateTime.class)
		)).thenReturn(attempt);

		when(datasetPreparationService.revalidate(DATASET_ID)).thenReturn(preparation(DatasetReadinessStatus.RETRY_REQUIRED));

		DatasetSourceRetryResponse response = service.retry(DATASET_ID, SOURCE_RESULT_ID, new DatasetSourceRetryRequest(source));

		assertThat(response.successful()).isFalse();
		assertThat(response.attemptNumber()).isEqualTo(1);
		assertThat(response.errorMessage()).contains(MAYA_API_UNAVAILABLE);
		assertThat(response.dataset().status()).isEqualTo(DatasetReadinessStatus.RETRY_REQUIRED);

		verify(datasetPreparationService, never()).recordRetrySuccess(any(), any(), any(), any(), any());
	}

	@Test
	void  shouldRejectRetryForDifferenceSourceUrl() {
		ApiIngestionRequest incorrectSource = new ApiIngestionRequest(1L, MAYA_SOURCE, MAYA_SOURCE + "-not", mapping());

		when(datasetPreparationService.getRetryableSource(DATASET_ID, SOURCE_RESULT_ID)).thenReturn(failedSource());

		assertThatThrownBy(() ->
				service.retry(DATASET_ID, SOURCE_RESULT_ID, new DatasetSourceRetryRequest(incorrectSource)
				)
		)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("URL does not match");

		verify(apiSymbolIngestionService, never()).ingestApi(any());
	}

	private DatasetPreparationResponse preparation(DatasetReadinessStatus status) {
		return new DatasetPreparationResponse(DATASET_ID, ANCIENT_SCRIPTS_PILOT, status, null, LocalDateTime.now(), null, null, null, null, null, null);
	}

	private DatasetPreparationSourceResponse failedSource() {
		return new DatasetPreparationSourceResponse(SOURCE_RESULT_ID, null, "API", MAYA_SOURCE, EXAMPLE_ORG_MAYA, IngestionStatus.FAILED, 0, 0, 0, "API unavailable", LocalDateTime.now());
	}

	private SourceFieldMapping mapping() {
		return new SourceFieldMapping("symbols", "symbolCode", "imageUrl", "label", null, null, null, null, null);
	}

	private ApiIngestionRequest sourceRequest() {
		return new ApiIngestionRequest(1l, MAYA_SOURCE, EXAMPLE_ORG_MAYA, mapping());
	}


}
