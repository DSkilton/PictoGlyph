package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ResearchDiscoveryPreview(String sourceId, String requestUrl, int totalMatches, List<Candidate> candidates, JsonNode rawResponse, String note) {
    public record Candidate(String sourceRecordId, String title, String objectUrl, List<String> imageUrls, String rights, JsonNode rawRecord, boolean explicitSignEvidence) {
        public Candidate(String sourceRecordId, String title, String objectUrl, List<String> imageUrls, String rights, JsonNode rawRecord) {
            this(sourceRecordId, title, objectUrl, imageUrls, rights, rawRecord, false);
        }
    }
}
