package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@RestController
@RequestMapping("/datasets/preparations")
@RequiredArgsConstructor
public class DatasetMlReleaseController {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatasetMlReleaseController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private final DatasetMlReleaseService service;

	@PostMapping("/{datasetPreparationId}/release-to-ml")
	public ReleaseDatasetToMlResponse release(@PathVariable Long datasetPreparationId, @Valid @RequestBody ReleaseDatasetToMlRequest request) {
		LOGGER.info("Starting ML release for dataset {}", datasetPreparationId);

		ReleaseDatasetToMlResponse response = service.release(datasetPreparationId, request.modelProfile());

		LOGGER.info("Completed ML release for dataset {}: symbols={}, createdJobs={}, existingJobs={}", datasetPreparationId, response.symbolCount(), response.createdJobCount(), response.existingJobCount());
		PROVENANCE_LOGGER.info("event=dataset_ml_release datasetId={} modelProfile={} symbolCount={} createdJobs={} existingJobs={}", datasetPreparationId, response.modelProfile(), response.symbolCount(), response.createdJobCount(), response.existingJobCount());

		return response;
	}
}
