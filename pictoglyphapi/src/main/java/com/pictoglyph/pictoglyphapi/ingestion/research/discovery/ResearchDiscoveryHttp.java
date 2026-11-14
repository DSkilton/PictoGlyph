package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class ResearchDiscoveryHttp {
    private static final Set<String> HOSTS = Set.of("collectionapi.metmuseum.org", "openaccess-api.clevelandart.org", "cdli.earth", "opencontext.org", "collections.louvre.fr");
    private final RestTemplate restTemplate;

    public ResearchDiscoveryHttp(RestTemplateBuilder builder) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(8)).setReadTimeout(Duration.ofSeconds(20)).build();
    }

    public JsonNode get(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Research API host is not allowlisted");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("application/ld+json")));
            headers.set(HttpHeaders.USER_AGENT, "PictoGlyph-MSc-research-discovery/1.0");
            ResponseEntity<JsonNode> response = restTemplate.exchange(new RequestEntity<>(headers, HttpMethod.GET, uri), JsonNode.class);
            if (response.getBody() == null) throw new IllegalStateException("Empty research API response");
            return response.getBody();
        } catch (RestClientException | IllegalStateException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Upstream research API could not be read: " + uri.getHost(), ex);
        }
    }

    RestTemplate restTemplate() { return restTemplate; }
}
