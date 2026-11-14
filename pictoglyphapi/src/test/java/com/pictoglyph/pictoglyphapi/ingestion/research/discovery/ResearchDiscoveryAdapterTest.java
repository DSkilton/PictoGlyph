package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class ResearchDiscoveryAdapterTest {
    private ResearchDiscoveryHttp http;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        http = new ResearchDiscoveryHttp(new RestTemplateBuilder());
        server = MockRestServiceServer.createServer(http.restTemplate());
    }

    @Test
    void metReadsPaginatedIdsAndPreservesOriginalObjectMetadata() {
        server.expect(method(GET)).andRespond(withSuccess("{\"total\":2,\"objectIDs\":[101]}", MediaType.APPLICATION_JSON));
        server.expect(method(GET)).andRespond(withSuccess("{\"objectID\":101,\"title\":\"Inscribed object\",\"objectURL\":\"https://www.metmuseum.org/art/collection/search/101\",\"isPublicDomain\":true,\"primaryImage\":\"https://images.metmuseum.org/test.jpg\",\"additionalImages\":[]}", MediaType.APPLICATION_JSON));
        ResearchDiscoveryPreview preview = new MetResearchDiscoveryAdapter(http).preview("hieroglyph", 1, 0);
        assertEquals(2, preview.totalMatches());
        assertEquals(1, preview.candidates().size());
        assertEquals("101", preview.candidates().getFirst().sourceRecordId());
        assertEquals("https://images.metmuseum.org/test.jpg", preview.candidates().getFirst().imageUrls().getFirst());
        assertTrue(preview.requestUrl().contains("/v1.1/search"));
        server.verify();
    }

    @Test
    void metDoesNotOfferImagesFromNonPublicDomainObjects() {
        server.expect(method(GET)).andRespond(withSuccess("{\"total\":1,\"objectIDs\":[101]}", MediaType.APPLICATION_JSON));
        server.expect(method(GET)).andRespond(withSuccess("{\"objectID\":101,\"isPublicDomain\":false,\"primaryImage\":\"https://images.metmuseum.org/test.jpg\"}", MediaType.APPLICATION_JSON));
        var preview = new MetResearchDiscoveryAdapter(http).preview("text", 1, 0);
        assertTrue(preview.candidates().getFirst().imageUrls().isEmpty());
        server.verify();
    }

    @Test
    void clevelandPreservesImagesAndObjectMetadata() {
        server.expect(method(GET)).andRespond(withSuccess("{\"info\":{\"total\":1},\"data\":[{\"id\":101,\"title\":\"Inscribed seal\",\"images\":{\"web\":{\"url\":\"https://image.clevelandart.org/example.jpg\"}}}]}", MediaType.APPLICATION_JSON));
        var preview = new ClevelandResearchDiscoveryAdapter(http).preview("seal", 2, 0);
        assertEquals(1, preview.candidates().size());
        assertTrue(preview.requestUrl().contains("cc0"));
        assertEquals("https://image.clevelandart.org/example.jpg", preview.candidates().getFirst().imageUrls().getFirst());
        server.verify();
    }

    @Test
    void cdliUsesBoundedPnumberAndDoesNotInventImageLinks() {
        server.expect(method(GET)).andRespond(withSuccess("{\"id\":\"P000001\",\"provenience\":\"Unknown\"}", MediaType.APPLICATION_JSON));
        var preview = new CdliResearchDiscoveryAdapter(http).preview("P000001", 1, 0);
        assertEquals("P000001", preview.candidates().getFirst().sourceRecordId());
        assertTrue(preview.candidates().getFirst().imageUrls().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CdliResearchDiscoveryAdapter(http).preview("https://bad.example/", 1, 0));
        server.verify();
    }

    @Test
    void openContextUsesMediaQueryAndRetainsRawResponse() {
        server.expect(method(GET)).andRespond(withSuccess("{\"totalResults\":300,\"@context\":{}}\n", MediaType.APPLICATION_JSON));
        var preview = new OpenContextResearchDiscoveryAdapter(http).preview("inscription", 3, 0);
        assertEquals(300, preview.totalMatches());
        assertTrue(preview.requestUrl().contains("type=media"));
        assertTrue(preview.candidates().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new OpenContextResearchDiscoveryAdapter(http).preview("inscription", 3, 20));
        server.verify();
    }

    @Test
    void louvreKeepsObjectImageAndCopyrightEvidence() {
        server.expect(method(GET)).andRespond(withSuccess("{\"arkId\":\"cl010277627\",\"title\":\"Inscribed fragment\",\"image\":[{\"urlImage\":\"https://collections.louvre.fr/media/cache/original/test.jpg\",\"copyright\":\"Museum photographer\"}]}", MediaType.APPLICATION_JSON));
        var preview = new LouvreResearchDiscoveryAdapter(http).preview("cl010277627", 1, 0);
        assertEquals("Inscribed fragment", preview.candidates().getFirst().title());
        assertEquals(1, preview.candidates().getFirst().imageUrls().size());
        assertEquals("Museum photographer", preview.candidates().getFirst().rights());
        assertThrows(IllegalArgumentException.class, () -> new LouvreResearchDiscoveryAdapter(http).preview("../../../", 1, 0));
        server.verify();
    }

    @Test
    void hostAllowlistRejectsUntrustedApiDomains() {
        assertThrows(IllegalArgumentException.class, () -> http.get(URI.create("https://example.org/anything")));
    }
}
