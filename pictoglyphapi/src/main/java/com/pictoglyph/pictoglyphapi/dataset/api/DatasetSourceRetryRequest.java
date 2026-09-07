package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DatasetSourceRetryRequest (

	@NotNull
	@Valid
	ApiIngestionRequest source
) {
}
