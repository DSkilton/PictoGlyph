package com.pictoglyph.pictoglyphapi.ingestion.api;

import jakarta.validation.constraints.NotNull;

public record RunApiSourceProfileRequest(
		@NotNull Long languageId
) {
}
