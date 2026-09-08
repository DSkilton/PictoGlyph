package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceRetryResponse;
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
public class DatasetSourceRetryController {

	private final DatasetSourceRetryService service;

	@PostMapping("/{datasetPreparationId}/sources/{sourceResultId}/retry")
	public DatasetSourceRetryResponse retry(
			@PathVariable Long datasetPreparationId,
			@PathVariable Long sourceResultId,
			@Valid
			@RequestBody
			DatasetSourceRetryRequest request
	) {
		return service.retry(datasetPreparationId, sourceResultId, request);
	}
}
