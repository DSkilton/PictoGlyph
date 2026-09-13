package com.pictoglyph.pictoglyphapi.dataset.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DatasetSourceProfileQueueItemRequest(
		@NotNull
		@Positive
		Long profileId,

		@NotNull
		@Positive
		Long languageId
) {
}
