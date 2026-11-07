package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets/preparations")
@RequiredArgsConstructor
public class DatasetSourceRetryController {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatasetSourceRetryController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final DatasetSourceRetryService service;

	@PostMapping("/{datasetPreparationId}/sources/{sourceResultId}/retry")
	public DatasetSourceRetryResponse retry(@PathVariable Long datasetPreparationId, @PathVariable Long sourceResultId) {
		LOGGER.info("Starting dataset source retry: datasetId={}, sourceResultId={}", datasetPreparationId, sourceResultId);

		DatasetSourceRetryResponse response;

		try {
			response = service.retry(datasetPreparationId, sourceResultId);
		} catch (RuntimeException e) {
			LOGGER.warn("Dataset source retry failed: datasetId={}, sourceResultId={}, errorType={}", datasetPreparationId, sourceResultId, e.getClass().getSimpleName());
			throw e;
		}

		if (response.successful()) {
			LOGGER.info("Dataset source retry finished: datasetId={}, sourceResultId={}, attempt={}, status={}", response.datasetPreparationId(), response.sourceResultId(), response.attemptNumber(), response.ingestionStatus());
		} else {
			LOGGER.warn("Dataset source retry finished with failure: datasetId={}, sourceResultId={}, attempt={}, status={}", response.datasetPreparationId(), response.sourceResultId(), response.attemptNumber(), response.ingestionStatus());
		}

		PROVENANCE_LOGGER.info("event=dataset_source_retry_recorded datasetId={} sourceResultId={} attempt={} ingestionJobId={} status={} importedCount={} skippedCount={} manualProcessingCount={}", response.datasetPreparationId(), response.sourceResultId(), response.attemptNumber(), response.ingestionJobId(), response.ingestionStatus(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());

		return response;
	}
}
