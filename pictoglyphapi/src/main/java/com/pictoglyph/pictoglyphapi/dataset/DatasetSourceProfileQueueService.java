package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueItemRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueItemResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ApiSourceProfileService;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiSourceProfileResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.RunApiSourceProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetSourceProfileQueueService {
	private final DatasetPreparationService datasetPreparationService;
	private final ApiSourceProfileService apiSourceProfileService;

	public DatasetSourceProfileQueueResponse run(DatasetSourceProfileQueueRequest request) {
		validateRequest(request);

		DatasetPreparationResponse dataset = datasetPreparationService.create(request.datasetName());
		Long datasetPreparationId = dataset.id();
		List<DatasetSourceProfileQueueItemResponse> sourceResults = new ArrayList<>();

		for (int i = 0; i < request.sources().size(); i++) {
			DatasetSourceProfileQueueItemRequest source = request.sources().get(i);
			DatasetSourceProfileQueueItemResponse result = processSource(datasetPreparationId, i, source);
			sourceResults.add(result);
		}

		DatasetPreparationResponse completedDataset = datasetPreparationService.completeIngestion(datasetPreparationId);
		int failedSourceCOunt = (int) sourceResults.stream()
				.filter(result ->
						result.ingestionStatus() == IngestionStatus.FAILED)
				.count();

		int completedSourceCount = sourceResults.size() - failedSourceCOunt;

		return new DatasetSourceProfileQueueResponse(completedDataset, sourceResults.size(), completedSourceCount, failedSourceCOunt, List.copyOf(sourceResults));
	}

	private DatasetSourceProfileQueueItemResponse processSource(Long dataPreparationId, int queueIndex, DatasetSourceProfileQueueItemRequest source) {
		ApiSourceProfileResponse profile = null;

		try {
			profile = apiSourceProfileService.findById(source.profileId());

			ApiIngestionResultResponse result = apiSourceProfileService.run(source.profileId(), new RunApiSourceProfileRequest(source.languageId()));
			datasetPreparationService.recordProfileIngestionResult(dataPreparationId, source.profileId(), source.languageId(), result);

			return new DatasetSourceProfileQueueItemResponse(queueIndex, source.profileId(), source.languageId(), result.sourceName(), result.sourcePath(), result.status(), result.ingestionJobId(), result.importedCount(), result.skippedCount(), result.manualProcessingCount(), null);

		} catch (RuntimeException e) {
			String sourceName = profile == null
					? "API profile " + source.profileId()
					: profile.sourceName();

			String sourceUrl = profile == null
					? null
					: profile.apiUrl();

			String errorMessage = safeErrorMessage(e);

			datasetPreparationService.recordProfileSourceFailure(dataPreparationId, source.profileId(), source.languageId(), sourceName, sourceUrl, errorMessage);

			return new DatasetSourceProfileQueueItemResponse(queueIndex, source.profileId(), source.languageId(), sourceName, sourceUrl, IngestionStatus.FAILED, null, 0, 0, 0, errorMessage);
		}
	}

	private String safeErrorMessage(RuntimeException e) {
		if (e.getMessage() == null || e.getMessage().isBlank()) {
			return e.getClass().getSimpleName();
		}

		return e.getMessage();
	}

	private void validateRequest(DatasetSourceProfileQueueRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Dataset profile queue request is required");
		}

		if (request.datasetName() == null || request.datasetName().isBlank()) {
			throw new IllegalArgumentException("Dataset name is required");
		}

		if (request.sources() == null || request.sources().isEmpty()) {
			throw new IllegalArgumentException("At least one API source profile is required");
		}

		for (int i = 0; i < request.sources().size(); i++) {
			DatasetSourceProfileQueueItemRequest source = request.sources().get(i);

			if (source == null) {
				throw new IllegalArgumentException("Source profile at queue index: " + i + " is required");
			}

			if (source.profileId() == null || source.profileId() <= 0) {
				throw new IllegalArgumentException("A valid source profile id is required at queue index: " + i);
			}

			if (source.languageId() == null || source.languageId() <= 0) {
				throw new IllegalArgumentException("A valid language id is required at queue index: " + i);
			}
		}
	}
}
