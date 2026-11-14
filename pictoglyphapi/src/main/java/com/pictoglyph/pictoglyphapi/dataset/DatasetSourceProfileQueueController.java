package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@RestController
@RequestMapping("/datasets/profile-source-queues")
@RequiredArgsConstructor
public class DatasetSourceProfileQueueController {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatasetSourceProfileQueueController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private final DatasetSourceProfileQueueService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	private DatasetSourceProfileQueueResponse run(@Valid @RequestBody DatasetSourceProfileQueueRequest request) {
		LOGGER.info("Starting dataset profile source queue");

		DatasetSourceProfileQueueResponse response;

		try {
			response = service.run(request);
		} catch (RuntimeException e) {
			LOGGER.warn("Dataset profile source queue failed: errorType={}", e.getClass().getSimpleName());
			throw e;
		}

		if (response.failedSourceCount() > 0) {
			LOGGER.warn("Dataset profile source queue finished with source failures: datasetId={}, completedSources={}, failedSources={}", response.dataset().id(), response.completedSourceCount(), response.failedSourceCount());
		} else {
			LOGGER.info("Dataset profile source queue finished: datasetId={}, completedSources={}", response.dataset().id(), response.completedSourceCount());
		}

		PROVENANCE_LOGGER.info("event=dataset_profile_source_queue_finished datasetId={} status={} sourceCount={} completedSourceCount={} failedSourceCount={}", response.dataset().id(), response.dataset().status(), response.sourceCount(), response.completedSourceCount(), response.failedSourceCount());

		return response;
	}
}
