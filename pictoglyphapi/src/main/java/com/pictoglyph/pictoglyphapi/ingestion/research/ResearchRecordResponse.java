package com.pictoglyph.pictoglyphapi.ingestion.research;

public record ResearchRecordResponse(
		Long recordId,
		boolean created,
		int imageAssetsCreated,
		int imageAssetsUpdated
) { }
