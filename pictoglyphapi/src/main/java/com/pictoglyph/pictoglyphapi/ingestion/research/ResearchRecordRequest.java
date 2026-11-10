package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResearchRecordRequest(
		@NotBlank String sourceId,
		@NotBlank String recordKey,
		@NotBlank String recordType,
		@NotBlank String sourceUrl,
		@NotNull JsonNode archaeologicalContext,
		@NotNull JsonNode rawRecord,
		@NotNull JsonNode provenance,
		@NotNull List<@Valid ResearchImageAssetRequest> images
) { }
