package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationSourceResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryResponse;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceRetryAttempt;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSourceProfileService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.RunApiSourceProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.pictoglyph.pictoglyphapi.utils.Constants.SOURCE_TYPE_API;

@Service
@RequiredArgsConstructor
public class DatasetSourceRetryService {

	private final DatasetPreparationService datasetPreparationService;
	private final ApiSourceProfileService apiSourceProfileService;

	public DatasetSourceRetryResponse retry(Long datasetPreparationId, Long sourceResultId) {
		DatasetPreparationSourceResponse target = datasetPreparationService.getRetryableSource(datasetPreparationId, sourceResultId);

		validateRetryContext(target);

		LocalDateTime startedAt = LocalDateTime.now();

		ApiIngestionResultResponse result;

		try {
			result = apiSourceProfileService.run(target.apiSourceProfileId(), new RunApiSourceProfileRequest(target.languageId()));

		} catch (RuntimeException exception) {
			LocalDateTime completedAt = LocalDateTime.now();

			DatasetPreparationSourceRetryAttempt attempt = datasetPreparationService.recordRetryFailure(datasetPreparationId, sourceResultId, safeErrorMessage(exception), startedAt, completedAt);
			DatasetPreparationResponse dataset = datasetPreparationService.revalidate(datasetPreparationId);

			return buildResponse(datasetPreparationId, sourceResultId, target, attempt, dataset);
		}

		LocalDateTime completedAt = LocalDateTime.now();
		DatasetPreparationSourceRetryAttempt attempt = datasetPreparationService.recordRetrySuccess(datasetPreparationId, sourceResultId, result, startedAt, completedAt);
		DatasetPreparationResponse dataset = datasetPreparationService.revalidate(datasetPreparationId);

		return buildResponse(datasetPreparationId, sourceResultId, target, attempt, dataset);
	}

	private DatasetSourceRetryResponse buildResponse(Long datasetPreparationId, Long sourceResultId, DatasetPreparationSourceResponse target, DatasetPreparationSourceRetryAttempt attempt, DatasetPreparationResponse dataset) {
		return new DatasetSourceRetryResponse(
				datasetPreparationId,
				sourceResultId,
				attempt.getAttemptNumber(),
				target.sourceName(),
				target.sourcePath(),
				attempt.getIngestionStatus(),
				attempt.getIngestionJobId(),
				attempt.getImportedCount(),
				attempt.getSkippedCount(),
				attempt.getManualProcessingCount(),
				attempt.getErrorMessage(),
				attempt.getStartedAt(),
				attempt.getCompletedAt(),
				dataset
		);
	}

	private String safeErrorMessage(RuntimeException exception) {
		if (exception.getMessage() == null || exception.getMessage().isBlank()) {
			return exception.getClass().getSimpleName();
		}

		return exception.getMessage();
	}

	private void validateRetryContext(DatasetPreparationSourceResponse target) {
		if (!SOURCE_TYPE_API.equalsIgnoreCase(target.sourceType())) {
			throw new IllegalStateException("This retry service only supports API sources");
		}

		if (target.apiSourceProfileId() == null || target.apiSourceProfileId() <= 0) {
			throw new IllegalStateException("Retry source does not have an API source profile");
		}

		if (target.languageId() == null || target.languageId() <= 0) {
			throw new IllegalStateException("Retry source does not have a language Id");
		}
	}
}
