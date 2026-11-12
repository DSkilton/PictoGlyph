package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CdliResearchDiscoveryAdapter implements ResearchDiscoveryAdapter {
    private final ResearchDiscoveryHttp http;

    @Override public String sourceId() { return "cdli-live"; }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        if (!query.matches("P[0-9]{6}")) throw new IllegalArgumentException("CDLI preview requires a P-number, e.g. P000001");
        URI uri = URI.create("https://cdli.earth/artifacts/" + query + ".json");
        JsonNode record = http.get(uri);
        ResearchDiscoveryPreview.Candidate item = new ResearchDiscoveryPreview.Candidate(query, record.path("title").asText(query),
                "https://cdli.earth/artifacts/" + query, List.of(), "REVIEW_ASSET_LEVEL_RIGHTS", record);
        return new ResearchDiscoveryPreview(sourceId(), uri.toString(), 1, List.of(item), record,
                "Metadata lookup only: CDLI asset references require further image-schema mapping. No inferred sign label.");
    }
}
