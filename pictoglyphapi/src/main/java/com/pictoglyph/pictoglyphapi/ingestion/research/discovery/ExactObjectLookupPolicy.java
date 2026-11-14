package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExactObjectLookupPolicy {
    static final int MAX_OBJECT_IDS_PER_REQUEST = 10;

    private ExactObjectLookupPolicy() {
    }

    static void validate(List<Integer> objectIds) {
        if (objectIds == null || objectIds.isEmpty() || objectIds.size() > MAX_OBJECT_IDS_PER_REQUEST) {
            throw invalidRequest();
        }
        Set<Integer> uniqueIds = new HashSet<>();
        for (Integer objectId : objectIds) {
            if (objectId == null || objectId <= 0 || !uniqueIds.add(objectId)) {
                throw invalidRequest();
            }
        }
    }

    private static IllegalArgumentException invalidRequest() {
        return new IllegalArgumentException("Provide 1-" + MAX_OBJECT_IDS_PER_REQUEST + " distinct, positive object IDs");
    }
}
