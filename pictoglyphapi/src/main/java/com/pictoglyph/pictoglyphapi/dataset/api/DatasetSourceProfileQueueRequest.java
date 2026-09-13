package com.pictoglyph.pictoglyphapi.dataset.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DatasetSourceProfileQueueRequest(
		@NotBlank
		String datasetName,

		@NotEmpty
		List<@Valid DatasetSourceProfileQueueItemRequest> sources
) {
}
