package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

public record ResearchDiscoveryImportResult(String sourceId, String requestUrl, int candidatesFound,
        int recordsCreated, int recordsReused, int imageReferencesCreated, int skippedWithoutIdentity, int signMappingProposals) { }
