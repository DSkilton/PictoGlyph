package com.pictoglyph.pictoglyphapi.dataset.api;

import jakarta.validation.constraints.NotBlank;

public record ReleaseDatasetToMlRequest(
		@NotBlank
		String modelProfile
) {
}
