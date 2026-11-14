package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClevelandResearchDiscoveryAdapter implements ResearchDiscoveryAdapter {
    private final ResearchDiscoveryHttp http;

    @Override public String sourceId() { return "cleveland-museum"; }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        URI uri = UriComponentsBuilder.fromUriString("https://openaccess-api.clevelandart.org/api/artworks/")
                .queryParam("q", query).queryParam("has_image", 1).queryParam("cc0")
                .queryParam("skip", offset).queryParam("limit", limit).build().encode().toUri();
        JsonNode response = http.get(uri);
        List<ResearchDiscoveryPreview.Candidate> candidates = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            String id = item.path("id").asText("");
            if (!id.matches("[0-9]+")) continue;
            List<String> images = new ArrayList<>();
            JsonNode image = item.path("images");
            for (String type : List.of("web", "print")) {
                String imageUrl = image.path(type).path("url").asText("");
                if (imageUrl.startsWith("https://") && !images.contains(imageUrl)) images.add(imageUrl);
            }
            candidates.add(new ResearchDiscoveryPreview.Candidate(id, item.path("title").asText(""),
                    "https://openaccess-api.clevelandart.org/api/artworks/" + id, List.copyOf(images), "CC0_FILTERED_API_RESPONSE", item));
        }
        int total = response.path("info").path("total").asInt(candidates.size());
        return new ResearchDiscoveryPreview(sourceId(), uri.toString(), total, List.copyOf(candidates), response,
                "CC0 filter applied; confirm artwork and source-image relevance before label assignment.");
    }
}
