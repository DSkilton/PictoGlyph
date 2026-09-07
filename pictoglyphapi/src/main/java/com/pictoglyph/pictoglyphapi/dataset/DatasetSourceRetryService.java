package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationSourceResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryResponse;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceRetryAttempt;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.pictoglyph.pictoglyphapi.utils.Constants.SOURCE_TYPE_API;

@Service
@RequiredArgsConstructor
public class DatasetSourceRetryService {

	private final DatasetPreparationService datasetPreparationService;
	private final ApiSymbolIngestionService apiSymbolIngestionService;

	public DatasetSourceRetryResponse retry(Long datasetPreparationId, Long sourceResultId, DatasetSourceRetryRequest request) {
		validateRequest(request);

		DatasetPreparationSourceResponse target = datasetPreparationService.getRetryableSource(datasetPreparationId, sourceResultId);

		validateSourceMatches(target, request.source());

		LocalDateTime startedAt = LocalDateTime.now();

		ApiIngestionResultResponse result;

		try {
			result = apiSymbolIngestionService.ingestApi(request.source());

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

	private void validateRequest(DatasetSourceRetryRequest request) {
		if (request == null || request.source() == null) {
			throw new IllegalArgumentException("Retry source is required");
		}
	}

	private void validateSourceMatches(DatasetPreparationSourceResponse target, ApiIngestionRequest request) {
		if (!SOURCE_TYPE_API.equalsIgnoreCase(target.sourceType())) {
			throw new IllegalStateException("This retry service only supports API sources");
		}

		if (!same(target.sourceName(), request.sourceName())) {
			throw new IllegalArgumentException("Retry source name does not match the failed source");
		}

		if (!same(target.sourcePath(), request.apiUrl())) {
			throw new IllegalArgumentException("Retry source URL does not match the failed source");
		}
	}

	private boolean same(String first, String second) {
		if (first == null || second == null) {
			return first == null && second == null;
		}

		return first.trim().equalsIgnoreCase(second.trim());
	}
}
