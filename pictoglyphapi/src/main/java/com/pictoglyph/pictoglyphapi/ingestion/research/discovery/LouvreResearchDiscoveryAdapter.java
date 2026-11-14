package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LouvreResearchDiscoveryAdapter implements ResearchDiscoveryAdapter {
    private final ResearchDiscoveryHttp http;

    @Override public String sourceId() { return "louvre-collections"; }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        if (!query.matches("cl[0-9]{9}")) throw new IllegalArgumentException("Louvre preview requires an ARK record ID such as cl010277627");
        URI uri = URI.create("https://collections.louvre.fr/ark:/53355/" + query + ".json");
        JsonNode record = http.get(uri);
        List<String> images = new ArrayList<>();
        List<String> credits = new ArrayList<>();
        for (JsonNode image : record.path("image")) {
            String url = image.path("urlImage").asText("");
            if (url.startsWith("https://collections.louvre.fr/media/") && !images.contains(url)) images.add(url);
            String credit = image.path("copyright").asText("");
            if (!credit.isBlank() && !credits.contains(credit)) credits.add(credit);
        }
        ResearchDiscoveryPreview.Candidate candidate = new ResearchDiscoveryPreview.Candidate(query, record.path("title").asText(""),
                "https://collections.louvre.fr/ark:/53355/" + query, List.copyOf(images),
                credits.isEmpty() ? "REVIEW_IMAGE_RIGHTS" : String.join("; ", credits), record);
        return new ResearchDiscoveryPreview(sourceId(), uri.toString(), 1, List.of(candidate), record,
                "Record-level image URLs exposed with original copyright credit. No blanket image-download permission assumed.");
    }
}
