package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenContextResearchDiscoveryAdapter implements ResearchDiscoveryAdapter {
    private final ResearchDiscoveryHttp http;

    @Override public String sourceId() { return "opencontext-archaeology"; }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        if (offset != 0) throw new IllegalArgumentException("Open Context preview supports the first page only; follow published next-page links in a future adapter revision");
        URI uri = UriComponentsBuilder.fromUriString("https://opencontext.org/query/.json")
                .queryParam("type", "media").queryParam("q", query).queryParam("rows", limit)
                .queryParam("response", "metadata,uri-meta").build().encode().toUri();
        JsonNode body = http.get(uri);
        return new ResearchDiscoveryPreview(sourceId(), uri.toString(), body.path("totalResults").asInt(0), List.of(), body,
                "Raw media-search JSON-LD retained. Per-record media URLs and project rights must be mapped before downloading.");
    }
}
