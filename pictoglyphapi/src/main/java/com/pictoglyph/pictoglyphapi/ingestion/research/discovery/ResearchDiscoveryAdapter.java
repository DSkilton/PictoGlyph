package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

public interface ResearchDiscoveryAdapter {
    String sourceId();
    ResearchDiscoveryPreview preview(String query, int limit, int offset);
}
