package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueItemResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetSourceQueueService {

	private static final String SOURCE_TYPE_API = "API";

	private final DatasetPreparationService datasetPreparationService;
	private final ApiSymbolIngestionService apiSymbolIngestionService;

	public DatasetSourceQueueResponse run(DatasetSourceQueueRequest request) {
		validateRequest(request);

		DatasetPreparationResponse dataset = datasetPreparationService.create(request.datasetName());

		Long datasetPreparationId = dataset.id();

		List<DatasetSourceQueueItemResponse> sourceResults = new ArrayList<>();

		for (int index = 0; index < request.sources().size(); index++) {
			ApiIngestionRequest source = request.sources().get(index);
			DatasetSourceQueueItemResponse result = processSource(datasetPreparationId, index, source);
			sourceResults.add(result);
		}

		DatasetPreparationResponse completedDataset = datasetPreparationService.completeIngestion(datasetPreparationId);

		int failedSourceCount = (int) sourceResults.stream()
				.filter(result ->
						result.ingestionStatus()== IngestionStatus.FAILED)
				.count();

		int completedSourceCount = sourceResults.size() - failedSourceCount;

		return new DatasetSourceQueueResponse(completedDataset, sourceResults.size(), completedSourceCount, failedSourceCount, List.copyOf(sourceResults));
	}

	private DatasetSourceQueueItemResponse processSource(Long datasetPreparationId, int queueIndex, ApiIngestionRequest source) {
		try {
			ApiIngestionResultResponse result = apiSymbolIngestionService.ingestApi(source);

			datasetPreparationService.recordIngestionResult(datasetPreparationId, result);

			return new DatasetSourceQueueItemResponse(queueIndex, result.sourceName(), result.sourcePath(), result.status(), result.ingestionJobId(), result.importedCount(), result.skippedCount(), result.manualProcessingCount(), null);

		} catch (RuntimeException e) {
			String errorMessage = safeErrorMessage(e);

			datasetPreparationService.recordSourceFailure(datasetPreparationId, SOURCE_TYPE_API, source.sourceName(), source.apiUrl(), errorMessage);

			/*
			 * Deliberately return a failed result
			 * rather than rethrowing.
			 *
			 * One broken source must not stop
			 * the remaining source queue.
			 */
			return new DatasetSourceQueueItemResponse(queueIndex, source.sourceName(), source.apiUrl(), IngestionStatus.FAILED, null, 0, 0, 0, errorMessage);
		}
	}

	private void validateRequest(DatasetSourceQueueRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Dataset source queue request is required");
		}

		if (request.datasetName() == null || request.datasetName().isBlank()) {
			throw new IllegalArgumentException("Dataset name is required");
		}

		if (request.sources() == null || request.sources().isEmpty()) {
			throw new IllegalArgumentException("At least one ingestion source is required");
		}

		for (int index = 0; index < request.sources().size(); index++) {
			if (request.sources().get(index) == null) {
				throw new IllegalArgumentException("Ingestion source at queue index " + index + " is required");
			}
		}
	}

	private String safeErrorMessage(RuntimeException exception) {
		if (exception.getMessage() == null || exception.getMessage().isBlank()) {

			return exception.getClass().getSimpleName();
		}

		return exception.getMessage();
	}
}