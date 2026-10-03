package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets/preparations")
@RequiredArgsConstructor
public class DatasetMlReleaseController {

	private final DatasetMlReleaseService service;

	@PostMapping("/{datasetPreparationId}/release-to-ml")
	public ReleaseDatasetToMlResponse release(@PathVariable Long datasetPreparationId, @Valid @RequestBody ReleaseDatasetToMlRequest request) {
		return service.release(datasetPreparationId, request.modelProfile());
	}
}
