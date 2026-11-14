package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MetResearchDiscoveryAdapter implements ResearchDiscoveryAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetResearchDiscoveryAdapter.class);
    private final ResearchDiscoveryHttp http;

    @Override public String sourceId() { return "met-openaccess-metadata"; }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        URI search = UriComponentsBuilder.fromUriString("https://collectionapi.metmuseum.org/public/collection/v1.1/search")
                .queryParam("q", query).queryParam("hasImages", true).queryParam("limit", limit).queryParam("offset", offset)
                .build().encode().toUri();
        JsonNode page = http.get(search);
        List<ResearchDiscoveryPreview.Candidate> candidates = new ArrayList<>();
        long started = System.nanoTime();
        for (JsonNode id : page.path("objectIDs")) {
            if (!id.canConvertToInt()) continue;
            URI detailUrl = URI.create("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + id.asInt());
            JsonNode object = http.get(detailUrl);
            List<String> images = new ArrayList<>();
            if (object.path("isPublicDomain").asBoolean(false)) {
                addImage(images, object.path("primaryImage").asText(""));
                for (JsonNode image : object.path("additionalImages")) addImage(images, image.asText(""));
            }
            candidates.add(new ResearchDiscoveryPreview.Candidate(
                    String.valueOf(id.asInt()), object.path("title").asText(""), object.path("objectURL").asText(""),
                    List.copyOf(images), object.path("isPublicDomain").asBoolean(false) ? "PUBLIC_DOMAIN_REPORTED_BY_SOURCE" : "REVIEW_IMAGE_RIGHTS", object));
            if (candidates.size() % 5 == 0) LOGGER.info("event=research_discovery_progress sourceId={} completed={} requested={} elapsedMs={}", sourceId(), candidates.size(), limit, (System.nanoTime() - started) / 1_000_000);
        }
        return new ResearchDiscoveryPreview(sourceId(), search.toString(), page.path("total").asInt(0), List.copyOf(candidates), page,
                "Candidate images require visual relevance checks; public-domain flags do not verify glyph labels.");
    }

    private static void addImage(List<String> images, String url) {
        if (url.startsWith("https://images.metmuseum.org/") && !images.contains(url)) images.add(url);
    }
}
