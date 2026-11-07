package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.CreateDatasetPreparationRequest;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationSourceResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.ExcludeDatasetPreparationRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/datasets/preparations")
@RequiredArgsConstructor
public class DatasetPreparationController {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatasetPreparationController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final DatasetPreparationService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DatasetPreparationResponse create(@RequestBody CreateDatasetPreparationRequest request) {
		LOGGER.info("Creating dataset preparation");

		DatasetPreparationResponse response = service.create(request.name());

		LOGGER.info("Created dataset preparation {}", response.id());
		PROVENANCE_LOGGER.info("event=dataset_preparation_created datasetId={} status={}", response.id(), response.status());

		return response;
	}

	@GetMapping("/{datasetPreparationId}")
	public DatasetPreparationResponse get(@PathVariable Long datasetPreparationId) {
		LOGGER.debug("Retrieving dataset preparation {}", datasetPreparationId);

		return service.get(datasetPreparationId);
	}

	@PostMapping("/{datasetPreparationId}/complete-ingestion")
	public DatasetPreparationResponse completeIngestion(@PathVariable Long datasetPreparationId) {
		LOGGER.info("Completing ingestion for dataset {}", datasetPreparationId);

		DatasetPreparationResponse response = service.completeIngestion(datasetPreparationId);

		LOGGER.info("Ingestion completion processed for dataset {}: status={}", datasetPreparationId, response.status());
		PROVENANCE_LOGGER.info("event=dataset_ingestion_completed datasetId={} status={}", datasetPreparationId, response.status());

		return response;
	}

	@PostMapping("/{datasetPreparationId}/validate")
	public DatasetPreparationResponse validate(@PathVariable Long datasetPreparationId) {
		LOGGER.info("Validating dataset {}", datasetPreparationId);

		DatasetPreparationResponse response = service.revalidate(datasetPreparationId);

		LOGGER.info("Dataset {} validation completed: status={}", datasetPreparationId, response.status());
		PROVENANCE_LOGGER.info("event=dataset_validation_completed datasetId={} status={}", datasetPreparationId, response.status());

		return response;
	}

	@PostMapping("/{datasetPreparationId}/exclude")
	public DatasetPreparationResponse exclude(@PathVariable Long datasetPreparationId, @RequestBody ExcludeDatasetPreparationRequest request){
		LOGGER.info("Excluding dataset {}", datasetPreparationId);

		DatasetPreparationResponse response = service.exclude(datasetPreparationId, request.reason());

		LOGGER.info("Dataset {} exclusion processed: status={}", datasetPreparationId, response.status());
		PROVENANCE_LOGGER.info("event=dataset_exclusion_processed datasetId={} status={}", datasetPreparationId, response.status());

		return response;
	}

	@GetMapping("/{datasetPreparationId}/sources")
	public List<DatasetPreparationSourceResponse> sources(@PathVariable Long datasetPreparationId) {
		LOGGER.debug("Listing sources for dataset {}", datasetPreparationId);

		return service.listSources(datasetPreparationId);
	}
}
