package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.CreateDatasetPreparationRequest;

import com.pictoglyph.pictoglyphapi.dataset.api.ExcludeDatasetPreparationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets/preparations")
@RequiredArgsConstructor
public class DatasetPreparationController {

	private final DatasetPreparationService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DatasetPreparationResponse create(@RequestBody CreateDatasetPreparationRequest request) {
		return service.create(request.name());
	}

	@GetMapping("/{datasetPreparationId}")
	public DatasetPreparationResponse get(@PathVariable Long datasetPreparationId) {
		return service.get(datasetPreparationId);
	}

	@PostMapping("/{datasetPreparationId}/complete-ingestion")
	public DatasetPreparationResponse completeIngestion(@PathVariable Long datasetPreparationId) {
		return service.completeIngestion(datasetPreparationId);
	}

	@PostMapping("/{datasetPreparationId}/validate")
	public DatasetPreparationResponse validate(@PathVariable Long datasetPreparationId) {
		return service.revalidate(datasetPreparationId);
	}

	@PostMapping("/{datasetPreparationId}/exclude")
	public DatasetPreparationResponse exclude(@PathVariable Long datasetPreparationId, @RequestBody ExcludeDatasetPreparationRequest request){
		return service.exclude(datasetPreparationId, request.reason());
	}
}
