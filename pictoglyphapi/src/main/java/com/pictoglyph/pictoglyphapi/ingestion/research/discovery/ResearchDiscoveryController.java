package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/ingestion/research-discovery")
@ConditionalOnProperty(name = "pictoglyph.research-discovery.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ResearchDiscoveryController {
    private final ResearchDiscoveryService service;

    @Value("${pictoglyph.research-import.token:}")
    private String importToken;

    @Value("${pictoglyph.research-import.enabled:false}")
    private boolean researchImportEnabled;

    @Value("${pictoglyph.research-discovery.import-enabled:false}")
    private boolean discoveryImportEnabled;

    @GetMapping("/{sourceId}/preview")
    public ResearchDiscoveryPreview preview(@PathVariable String sourceId, @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit, @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "X-PictoGlyph-Import-Token", required = false) String suppliedToken) {
        authenticate(suppliedToken);
        validate(q, limit, offset);
        try {
            return service.preview(sourceId, q.trim(), limit, offset);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/{sourceId}/import")
    public ResearchDiscoveryImportResult importPage(@PathVariable String sourceId, @RequestParam String q,
            @RequestParam(defaultValue = "2") int limit, @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "X-PictoGlyph-Import-Token", required = false) String suppliedToken) {
        authenticate(suppliedToken);
        if (!researchImportEnabled || !discoveryImportEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Research discovery import is disabled");
        }
        validate(q, limit, offset);
        try {
            return service.importPage(sourceId, q.trim(), limit, offset);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void authenticate(String suppliedToken) {
        if (importToken == null || importToken.isBlank() || suppliedToken == null ||
                !MessageDigest.isEqual(importToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Research token missing or invalid");
        }
    }

    private static void validate(String query, int limit, int offset) {
        if (query == null || query.isBlank() || query.length() > 80 || limit < 1 || limit > 20 || offset < 0 || offset > 9980) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query length 1-80, limit 1-20, offset 0-9980 required");
        }
    }
}
