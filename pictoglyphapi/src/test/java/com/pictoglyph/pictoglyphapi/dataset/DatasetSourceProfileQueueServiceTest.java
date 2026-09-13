package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueItemRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSourceProfileService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiSourceProfileResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.RunApiSourceProfileRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.pictoglyph.pictoglyphapi.TestConstants.ANCIENT_SCRIPTS_PILOT;
import static com.pictoglyph.pictoglyphapi.TestConstants.EGYPTIAN_PROFILE;
import static com.pictoglyph.pictoglyphapi.TestConstants.EGYPTIAN_SOURCE;
import static com.pictoglyph.pictoglyphapi.TestConstants.EXAMPLE_ORG_EGYPTIAN;
import static com.pictoglyph.pictoglyphapi.TestConstants.EXAMPLE_ORG_MAYA;
import static com.pictoglyph.pictoglyphapi.TestConstants.MAYA_PROFILE;
import static com.pictoglyph.pictoglyphapi.TestConstants.MAYA_SOURCE;
import static com.pictoglyph.pictoglyphapi.dataset.DatasetSourceQueueServiceTest.API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetSourceProfileQueueServiceTest {

	@Mock
	private DatasetPreparationService datasetPreparationService;

	@Mock
	private ApiSourceProfileService apiSourceProfileService;

	private DatasetSourceProfileQueueService service;

	@BeforeEach
	void setUp() {
		service = new DatasetSourceProfileQueueService(datasetPreparationService, apiSourceProfileService);
	}

	@Test
	void shouldRunApprovedProfilesInQueue() {
		DatasetSourceProfileQueueRequest request = new DatasetSourceProfileQueueRequest(
				ANCIENT_SCRIPTS_PILOT,
				List.of(
						new DatasetSourceProfileQueueItemRequest(3L, 1L),
						new DatasetSourceProfileQueueItemRequest(4L, 2L)
				)
		);

		when(datasetPreparationService.create(ANCIENT_SCRIPTS_PILOT)).thenReturn(preparation(DatasetReadinessStatus.INGESTING));

		ApiSourceProfileResponse maya = profile(3L, MAYA_PROFILE, MAYA_SOURCE, EXAMPLE_ORG_MAYA);
		ApiSourceProfileResponse egyptian = profile(4L, EGYPTIAN_PROFILE, EGYPTIAN_SOURCE, EXAMPLE_ORG_EGYPTIAN);

		when(apiSourceProfileService.findById(3L)).thenReturn(maya);
		when(apiSourceProfileService.findById(4L)).thenReturn(egyptian);

		ApiIngestionResultResponse mayaResult = result(100L, maya, 10L);
		ApiIngestionResultResponse egyptianResult = result(101L, egyptian, 20L);

		when(apiSourceProfileService.run(3L, new RunApiSourceProfileRequest(1L))).thenReturn(mayaResult);
		when(apiSourceProfileService.run(4L, new RunApiSourceProfileRequest(2L))).thenReturn(egyptianResult);
		when(datasetPreparationService.completeIngestion(1L)).thenReturn(preparation(DatasetReadinessStatus.READY_FOR_ML));

		DatasetSourceProfileQueueResponse response = service.run(request);

		assertThat(response.sourceCount()).isEqualTo(2);
		assertThat(response.completedSourceCount()).isEqualTo(2);
		assertThat(response.failedSourceCount()).isZero();
		assertThat(response.dataset().status()).isEqualTo(DatasetReadinessStatus.READY_FOR_ML);

		verify(datasetPreparationService).recordProfileIngestionResult(1L, 3L, 1L, mayaResult);
		verify(datasetPreparationService).recordProfileIngestionResult(1L, 4L, 2L, egyptianResult);
	}

	@Test
	void shouldRecordProfileFailureAndContinueQueue() {
		DatasetSourceProfileQueueRequest request = new DatasetSourceProfileQueueRequest(ANCIENT_SCRIPTS_PILOT,
				List.of(
						new DatasetSourceProfileQueueItemRequest(3L, 1L),
						new DatasetSourceProfileQueueItemRequest(4L, 2L)
				)
		);

		when(datasetPreparationService.create(ANCIENT_SCRIPTS_PILOT)).thenReturn(preparation(DatasetReadinessStatus.INGESTING));

		ApiSourceProfileResponse maya = profile(3L, MAYA_PROFILE, MAYA_SOURCE, EXAMPLE_ORG_MAYA);
		ApiSourceProfileResponse egyptian = profile(4L, EGYPTIAN_PROFILE, EGYPTIAN_SOURCE, EXAMPLE_ORG_EGYPTIAN);

		when(apiSourceProfileService.findById(3L)).thenReturn(maya);
		when(apiSourceProfileService.findById(4L)).thenReturn(egyptian);
		when(apiSourceProfileService.run(3L, new RunApiSourceProfileRequest(1L)))
				.thenThrow(new IllegalStateException("Breaking API schema drift detected"));

		ApiIngestionResultResponse egyptianResult = result(101L, egyptian, 20L);

		when(apiSourceProfileService.run(4L, new RunApiSourceProfileRequest(2L))).thenReturn(egyptianResult);
		when(datasetPreparationService.completeIngestion(1L)).thenReturn(preparation(DatasetReadinessStatus.RETRY_REQUIRED));

		DatasetSourceProfileQueueResponse response = service.run(request);

		assertThat(response.sourceCount()).isEqualTo(2);
		assertThat(response.completedSourceCount()).isEqualTo(1);
		assertThat(response.failedSourceCount()).isEqualTo(1);
		assertThat(response.sources().get(0).errorMessage()).contains("schema drift");
		assertThat(response.sources().get(1).sourceName()).isEqualTo(EGYPTIAN_SOURCE);
		verify(datasetPreparationService).recordProfileSourceFailure(1L, 3L, 1L, MAYA_SOURCE, EXAMPLE_ORG_MAYA, "Breaking API schema drift detected");

		/*
		 * The critical assertion:
		 * Maya failed but Egyptian still ran.
		 */
		verify(apiSourceProfileService).run(4L, new RunApiSourceProfileRequest(2L));
	}

	private ApiSourceProfileResponse profile(Long id, String profileName, String sourceName, String apiUrl) {
		SourceFieldMapping mapping = new SourceFieldMapping("symbols", "symbolCode", "imageUrl", "label", null, null, null, null, null);

		LocalDateTime now = LocalDateTime.now();

		return new ApiSourceProfileResponse(id, profileName, sourceName, apiUrl, ApiSourceProfileStatus.APPROVED, mapping,
				List.of("symbolCode", "imageUrl", "label"), now, now, now, now);
	}

	private ApiIngestionResultResponse result(Long jobId, ApiSourceProfileResponse profile, Long symbolId) {
		return new ApiIngestionResultResponse(jobId, API, profile.sourceName(), profile.apiUrl(), IngestionStatus.COMPLETED, 1, 0, 0, List.of(symbolId), List.of());
	}

	private DatasetPreparationResponse preparation(DatasetReadinessStatus status) {
		return new DatasetPreparationResponse(1L, ANCIENT_SCRIPTS_PILOT, status, null, null, null, null, null, null, null, null);
	}
}