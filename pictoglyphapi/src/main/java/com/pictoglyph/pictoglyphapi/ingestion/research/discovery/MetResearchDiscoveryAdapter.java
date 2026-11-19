package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class MetResearchDiscoveryAdapter implements ResearchObjectLookupAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetResearchDiscoveryAdapter.class);
    private static final String SOURCE_ID = "met-openaccess-metadata";
    private static final String SEARCH_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1.1/search";
    private static final String OBJECT_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1/objects/";
    private static final String PUBLIC_DOMAIN_RIGHTS = "PUBLIC_DOMAIN_REPORTED_BY_SOURCE";
    private static final String REVIEW_IMAGE_RIGHTS = "REVIEW_IMAGE_RIGHTS";
    private static final String IMAGE_HOST_PREFIX = "https://images.metmuseum.org/";
    private static final int PROGRESS_LOG_INTERVAL = 5;
    private final ResearchDiscoveryHttp http;

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public ResearchDiscoveryPreview preview(String query, int limit, int offset) {
        URI search = UriComponentsBuilder.fromUriString(SEARCH_ENDPOINT)
                .queryParam("q", query).queryParam("hasImages", true).queryParam("limit", limit).queryParam("offset", offset)
                .build().encode().toUri();
        JsonNode page = http.get(search);
        List<ResearchDiscoveryPreview.Candidate> candidates = new ArrayList<>();
        long started = System.nanoTime();
        for (JsonNode id : page.path("objectIDs")) {
            if (!id.canConvertToInt()) continue;
            candidates.add(fetchObject(id.asInt()));
            if (candidates.size() % PROGRESS_LOG_INTERVAL == 0) {
                LOGGER.info("event=research_discovery_progress sourceId={} completed={} requested={} elapsedMs={}",
                        sourceId(), candidates.size(), limit, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
        }
        return new ResearchDiscoveryPreview(sourceId(), search.toString(), page.path("total").asInt(0), List.copyOf(candidates), page,
                "Candidate images require visual relevance checks; public-domain flags do not verify glyph labels.");
    }

    @Override
    public ResearchDiscoveryPreview previewObjectIds(List<Integer> objectIds) {
        ExactObjectLookupPolicy.validate(objectIds);
        List<ResearchDiscoveryPreview.Candidate> candidates = new ArrayList<>();
        ArrayNode requestedIds = JsonNodeFactory.instance.arrayNode();
        for (Integer objectId : objectIds) {
            candidates.add(fetchObject(objectId));
            requestedIds.add(objectId);
        }
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.set("requestedObjectIds", requestedIds);
        evidence.put("retrievalMethod", "EXACT_OBJECT_ID");
        return new ResearchDiscoveryPreview(sourceId(), OBJECT_ENDPOINT, candidates.size(), List.copyOf(candidates), evidence,
                "Exact-ID candidates; image links are remote references and signs are not verified.");
    }

    private ResearchDiscoveryPreview.Candidate fetchObject(int requestedId) {
        if (requestedId <= 0) throw new IllegalArgumentException("Met object ID must be positive");
        JsonNode object = http.get(URI.create(OBJECT_ENDPOINT + requestedId));
        if (!object.path("objectID").canConvertToInt() || object.path("objectID").asInt() != requestedId) {
            throw new IllegalStateException("Met response object ID differs from requested ID " + requestedId);
        }
        boolean publicDomain = object.path("isPublicDomain").asBoolean(false);
        List<String> images = new ArrayList<>();
        if (publicDomain) {
            addImage(images, object.path("primaryImage").asText(""));
            for (JsonNode image : object.path("additionalImages")) addImage(images, image.asText(""));
        }
        return new ResearchDiscoveryPreview.Candidate(String.valueOf(requestedId), object.path("title").asText(""),
                object.path("objectURL").asText(""), List.copyOf(images), publicDomain ? PUBLIC_DOMAIN_RIGHTS : REVIEW_IMAGE_RIGHTS, object);
    }

    private static void addImage(List<String> images, String url) {
        if (url.startsWith(IMAGE_HOST_PREFIX) && !images.contains(url)) images.add(url);
    }
}
