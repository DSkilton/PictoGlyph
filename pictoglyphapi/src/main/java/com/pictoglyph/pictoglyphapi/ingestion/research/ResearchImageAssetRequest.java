package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResearchImageAssetRequest(
		@NotBlank String assetKey,
		@NotBlank String sourcePath,
		@NotBlank String imageKind,
		String rights,
		String checksumSha256,
		@NotNull JsonNode analysis
) { }
