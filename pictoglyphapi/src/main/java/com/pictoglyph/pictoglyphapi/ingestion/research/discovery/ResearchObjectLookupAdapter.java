package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import java.util.List;

public interface ResearchObjectLookupAdapter extends ResearchDiscoveryAdapter {
    ResearchDiscoveryPreview previewObjectIds(List<Integer> objectIds);
}
