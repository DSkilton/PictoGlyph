package com.pictoglyph.pictoglyphapi.ingestion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FolderIngestionRequest(
		@NotNull Long languageId,
		@NotBlank String folderPath
) {
}
