package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetLookupExactPolicyTest {
    private static final String OBJECT_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1/objects/";
    private static final int FIRST_OBJECT_ID = 550876;
    private static final int SECOND_OBJECT_ID = 550879;
    private MetResearchDiscoveryAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ResearchDiscoveryHttp http = new ResearchDiscoveryHttp(new RestTemplateBuilder());
        server = MockRestServiceServer.createServer(http.restTemplate());
        adapter = new MetResearchDiscoveryAdapter(http);
    }

    @Test
    void retrievesRequestedObjectsInOrderAndRetainsMuseumEvidence() {
        server.expect(requestTo(OBJECT_ENDPOINT + FIRST_OBJECT_ID)).andExpect(method(GET)).andRespond(withSuccess("""
                {
                  "objectID": 550876,
                  "title": "Inlay, hieroglyph",
                  "objectURL": "https://www.metmuseum.org/art/collection/search/550876",
                  "department": "Egyptian Art",
                  "accessionNumber": "26.3.164i",
                  "isPublicDomain": true,
                  "primaryImage": "https://images.metmuseum.org/first.jpg",
                  "additionalImages": ["https://images.metmuseum.org/second.jpg"]
                }
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(OBJECT_ENDPOINT + SECOND_OBJECT_ID)).andExpect(method(GET)).andRespond(withSuccess("""
                {
                  "objectID": 550879,
                  "title": "Inlay, hieroglyph",
                  "isPublicDomain": true,
                  "primaryImage": "https://images.metmuseum.org/third.jpg",
                  "additionalImages": []
                }
                """, MediaType.APPLICATION_JSON));

        ResearchDiscoveryPreview preview = adapter.previewObjectIds(List.of(FIRST_OBJECT_ID, SECOND_OBJECT_ID));

        assertEquals("met-openaccess-metadata", preview.sourceId());
        assertEquals(2, preview.totalMatches());
        assertEquals(2, preview.candidates().size());
        assertEquals("EXACT_OBJECT_ID", preview.rawResponse().path("retrievalMethod").asText());
        assertEquals(FIRST_OBJECT_ID, preview.rawResponse().path("requestedObjectIds").get(0).asInt());
        assertEquals(SECOND_OBJECT_ID, preview.rawResponse().path("requestedObjectIds").get(1).asInt());
        assertEquals(String.valueOf(FIRST_OBJECT_ID), preview.candidates().get(0).sourceRecordId());
        assertEquals(String.valueOf(SECOND_OBJECT_ID), preview.candidates().get(1).sourceRecordId());
        assertEquals("26.3.164i", preview.candidates().get(0).rawRecord().path("accessionNumber").asText());
        assertEquals("Egyptian Art", preview.candidates().get(0).rawRecord().path("department").asText());
        assertEquals(List.of("https://images.metmuseum.org/first.jpg", "https://images.metmuseum.org/second.jpg"), preview.candidates().get(0).imageUrls());
        assertEquals("PUBLIC_DOMAIN_REPORTED_BY_SOURCE", preview.candidates().get(0).rights());
        assertFalse(preview.candidates().get(0).explicitSignEvidence());
        server.verify();
    }

    @Test
    void retainsRestrictedObjectMetadataWithoutOfferingItsImages() {
        server.expect(requestTo(OBJECT_ENDPOINT + FIRST_OBJECT_ID)).andExpect(method(GET)).andRespond(withSuccess("""
                {
                  "objectID": 550876,
                  "isPublicDomain": false,
                  "primaryImage": "https://images.metmuseum.org/restricted.jpg",
                  "additionalImages": []
                }
                """, MediaType.APPLICATION_JSON));

        ResearchDiscoveryPreview preview = adapter.previewObjectIds(List.of(FIRST_OBJECT_ID));

        assertEquals("REVIEW_IMAGE_RIGHTS", preview.candidates().getFirst().rights());
        assertTrue(preview.candidates().getFirst().imageUrls().isEmpty());
        assertEquals("https://images.metmuseum.org/restricted.jpg", preview.candidates().getFirst().rawRecord().path("primaryImage").asText());
        server.verify();
    }

    @Test
    void rejectsDuplicatesInvalidIdentifiersAndOversizedBatchesBeforeCallingMuseum() {
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(List.of()));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(List.of(FIRST_OBJECT_ID, FIRST_OBJECT_ID)));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(Arrays.asList(FIRST_OBJECT_ID, null)));
        assertThrows(IllegalArgumentException.class, () -> adapter.previewObjectIds(IntStream.rangeClosed(1, 11).boxed().toList()));
        server.verify();
    }

    @Test
    void rejectsUpstreamObjectIdMismatch() {
        server.expect(requestTo(OBJECT_ENDPOINT + FIRST_OBJECT_ID)).andExpect(method(GET)).andRespond(withSuccess("""
                {"objectID": 550879, "isPublicDomain": true}
                """, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> adapter.previewObjectIds(List.of(FIRST_OBJECT_ID)));
        server.verify();
    }

    @Test
    void ignoresUntrustedImageUrlsAndDeduplicatesMuseumImages() {
        server.expect(requestTo(OBJECT_ENDPOINT + FIRST_OBJECT_ID)).andExpect(method(GET)).andRespond(withSuccess("""
                {
                  "objectID": 550876,
                  "isPublicDomain": true,
                  "primaryImage": "https://images.metmuseum.org/first.jpg",
                  "additionalImages": [
                    "https://images.metmuseum.org/first.jpg",
                    "https://example.org/untrusted.jpg",
                    "http://images.metmuseum.org/insecure.jpg"
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        ResearchDiscoveryPreview preview = adapter.previewObjectIds(List.of(FIRST_OBJECT_ID));

        assertEquals(List.of("https://images.metmuseum.org/first.jpg"), preview.candidates().getFirst().imageUrls());
        server.verify();
    }
}
