package com.pictoglyph.pictoglyphapi.ingestion.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApiSourceProfileRequest (
		@NotBlank String profileName,
		@NotBlank String sourceName,
		@NotBlank String apiUrl,

		@NotNull
		@Valid
		SourceFieldMapping sourceFieldMapping
){
}
