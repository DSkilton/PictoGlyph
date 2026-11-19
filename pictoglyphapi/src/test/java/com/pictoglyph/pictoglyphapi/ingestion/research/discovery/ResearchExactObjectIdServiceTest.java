package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.SourceMappingPipeline;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordRequest;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordResponse;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResearchExactObjectIdServiceTest {
    private static final String SOURCE_ID = "met-openaccess-metadata";
    private static final int OBJECT_ID = 550876;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResearchObjectLookupAdapter lookup = mock(ResearchObjectLookupAdapter.class);
    private final ResearchRecordService records = mock(ResearchRecordService.class);
    private final SourceMappingPipeline mappingPipeline = mock(SourceMappingPipeline.class);
    private ResearchDiscoveryService service;

    @BeforeEach
    void setUp() {
        when(lookup.sourceId()).thenReturn(SOURCE_ID);
        service = newService(lookup);
    }

    @Test
    void previewsExactIdsWithoutPersistingOrInvokingSignMapping() {
        ResearchDiscoveryPreview preview = preview();

        when(lookup.previewObjectIds(List.of(OBJECT_ID))).thenReturn(preview);

        ResearchDiscoveryPreview result = service.previewObjectIds(SOURCE_ID, List.of(OBJECT_ID));

        assertEquals(preview, result);
        verifyNoInteractions(records, mappingPipeline);
        verify(lookup).previewObjectIds(List.of(OBJECT_ID));
    }

    @Test
    void importsExactIdsThroughExistingResearchRecordPersistence() {
        when(lookup.previewObjectIds(List.of(OBJECT_ID))).thenReturn(preview());
        when(records.importRecord(any(ResearchRecordRequest.class))).thenReturn(new ResearchRecordResponse(123L, true, 2, 0));

        ResearchDiscoveryImportResult result = service.importObjectIds(SOURCE_ID, List.of(OBJECT_ID));

        assertEquals(1, result.candidatesFound());
        assertEquals(1, result.recordsCreated());
        assertEquals(0, result.recordsReused());
        assertEquals(2, result.imageReferencesCreated());
        assertEquals(0, result.signMappingProposals());

        ArgumentCaptor<ResearchRecordRequest> captured = ArgumentCaptor.forClass(ResearchRecordRequest.class);
        verify(records).importRecord(captured.capture());
        ResearchRecordRequest request = captured.getValue();

        assertEquals(SOURCE_ID, request.sourceId());
        assertEquals("object:" + OBJECT_ID, request.recordKey());
        assertEquals("ARTEFACT", request.recordType());
        assertEquals("26.3.164i", request.rawRecord().path("accessionNumber").asText());
        assertEquals(OBJECT_ID, request.rawRecord().path("objectID").asInt());
        assertEquals("API_METADATA_REFERENCE", request.provenance().path("acquisitionMode").asText());
        assertEquals("NOT_APPLICABLE_ARTEFACT", request.provenance().path("signMappingStatus").asText());
        assertFalse(request.provenance().path("trainingDataApproved").asBoolean());
        assertEquals(2, request.images().size());
        verifyNoInteractions(mappingPipeline);
    }

    @Test
    void repeatedImportCanReuseTheExistingDatabaseRecord() {
        when(lookup.previewObjectIds(List.of(OBJECT_ID))).thenReturn(preview());
        when(records.importRecord(any(ResearchRecordRequest.class))).thenReturn(new ResearchRecordResponse(123L, false, 0, 0));

        ResearchDiscoveryImportResult result = service.importObjectIds(SOURCE_ID, List.of(OBJECT_ID));

        assertEquals(0, result.recordsCreated());
        assertEquals(1, result.recordsReused());

        verify(records).importRecord(any(ResearchRecordRequest.class));
    }

    @Test
    void unsupportedSourceIsRejectedWithoutLookingUpRecords() {
        ResearchDiscoveryAdapter keywordOnly = mock(ResearchDiscoveryAdapter.class);

        when(keywordOnly.sourceId()).thenReturn("keyword-only-source");

        ResearchDiscoveryService otherService = newService(keywordOnly);

        ResponseStatusException unsupported = assertThrows(ResponseStatusException.class,
                () -> otherService.previewObjectIds("keyword-only-source", List.of(OBJECT_ID)));

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.previewObjectIds("unregistered", List.of(OBJECT_ID)));

        assertEquals(HttpStatus.NOT_FOUND, unsupported.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());

        verifyNoInteractions(records);
    }

    @Test
    void invalidIdsAreRejectedBeforeContactingTheMuseum() {
        assertThrows(IllegalArgumentException.class, () -> service.previewObjectIds(SOURCE_ID, Arrays.asList((Integer) null)));
        assertThrows(IllegalArgumentException.class, () -> service.previewObjectIds(SOURCE_ID, List.of(0)));

        verify(lookup, never()).previewObjectIds(any());

        verifyNoInteractions(records);
    }

    private ResearchDiscoveryService newService(ResearchDiscoveryAdapter adapter) {
        return new ResearchDiscoveryService(List.of(adapter), records, mappingPipeline,
                new SourceFieldDiscoveryService(),
                new SourceMappingValidator(new SourceFieldValueReader()), mapper);
    }

    private ResearchDiscoveryPreview preview() {
        ObjectNode raw = mapper.createObjectNode().put("objectID", OBJECT_ID).put("accessionNumber", "26.3.164i");
        ResearchDiscoveryPreview.Candidate candidate = new ResearchDiscoveryPreview.Candidate(String.valueOf(OBJECT_ID), "Inlay, hieroglyph",
                "https://www.metmuseum.org/art/collection/search/550876",
                List.of("https://images.metmuseum.org/one.jpg", "https://images.metmuseum.org/two.jpg"),
                "PUBLIC_DOMAIN_REPORTED_BY_SOURCE", raw);

        return new ResearchDiscoveryPreview(SOURCE_ID, "https://collectionapi.metmuseum.org/public/collection/v1/objects/",
                1, List.of(candidate), mapper.createObjectNode(), "Exact-ID preview");
    }
}
