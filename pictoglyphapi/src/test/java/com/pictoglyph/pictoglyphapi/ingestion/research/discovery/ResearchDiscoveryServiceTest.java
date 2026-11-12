package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.SourceMappingPipeline;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordRequest;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordResponse;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResearchDiscoveryServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResearchDiscoveryAdapter adapter = mock(ResearchDiscoveryAdapter.class);
    private final ResearchRecordService recordService = mock(ResearchRecordService.class);
    private final SourceMappingPipeline mappingPipeline = mock(SourceMappingPipeline.class);
    private ResearchDiscoveryService service;

    @BeforeEach
    void setup() {
        when(adapter.sourceId()).thenReturn("museum-test");
        service = new ResearchDiscoveryService(List.of(adapter), recordService, mappingPipeline,
                new SourceFieldDiscoveryService(), new SourceMappingValidator(new SourceFieldValueReader()), mapper);
    }

    @Test
    void museumObjectUsesExistingResearchPersistenceWithoutSignMapping() {
        ObjectNode raw = mapper.createObjectNode().put("objectID", 42).put("title", "Seal");
        ResearchDiscoveryPreview.Candidate item = new ResearchDiscoveryPreview.Candidate("42", "Seal", "https://example.org/42",
                List.of("https://images.example.org/42.jpg", "https://images.example.org/42.jpg"), "PUBLIC_DOMAIN_REPORTED_BY_SOURCE", raw);
        when(adapter.preview("seal", 1, 0)).thenReturn(new ResearchDiscoveryPreview("museum-test", "https://example.org/search?q=seal", 1,
                List.of(item), mapper.createObjectNode(), "test"));
        when(recordService.importRecord(any())).thenReturn(new ResearchRecordResponse(9L, true, 1, 0));

        ResearchDiscoveryImportResult result = service.importPage("museum-test", "seal", 1, 0);

        assertEquals(1, result.recordsCreated());
        assertEquals(1, result.imageReferencesCreated());
        assertEquals(0, result.signMappingProposals());
        verifyNoInteractions(mappingPipeline);
        ArgumentCaptor<ResearchRecordRequest> captor = ArgumentCaptor.forClass(ResearchRecordRequest.class);
        verify(recordService).importRecord(captor.capture());
        ResearchRecordRequest request = captor.getValue();
        assertEquals("museum-test", request.sourceId());
        assertEquals("object:42", request.recordKey());
        assertEquals("ARTEFACT", request.recordType());
        assertEquals(1, request.images().size());
        assertNull(request.images().getFirst().checksumSha256());
        assertEquals("REMOTE_REFERENCE_ONLY", request.images().getFirst().analysis().path("downloadStatus").asText());
        assertEquals("NOT_APPLICABLE_ARTEFACT", request.provenance().path("signMappingStatus").asText());
        assertFalse(request.provenance().path("trainingDataApproved").asBoolean());
    }

    @Test
    void sourceDeclaredSignEvidenceUsesExistingMappingChainWithoutCreatingSymbol() {
        ObjectNode raw = mapper.createObjectNode().put("symbolCode", "A1").put("imageUrl", "https://images.example.org/glyph.jpg");
        ResearchDiscoveryPreview.Candidate item = new ResearchDiscoveryPreview.Candidate("glyph-7", "Known sign",
                "https://example.org/glyph-7", List.of("https://images.example.org/glyph.jpg"), "SOURCE_RIGHTS", raw, true);
        when(adapter.preview("glyph", 1, 0)).thenReturn(new ResearchDiscoveryPreview("museum-test", "https://example.org/signs", 1,
                List.of(item), mapper.createObjectNode(), "test"));
        when(mappingPipeline.resolve(any())).thenReturn(Map.of(
                SourceMappingTarget.SYMBOL_CODE, new FieldMatch(SourceMappingTarget.SYMBOL_CODE, "symbolCode", 0.95, "known sign field"),
                SourceMappingTarget.IMAGE_PATH, new FieldMatch(SourceMappingTarget.IMAGE_PATH, "imageUrl", 0.95, "known image field")));
        when(recordService.importRecord(any())).thenReturn(new ResearchRecordResponse(10L, true, 1, 0));

        ResearchDiscoveryImportResult result = service.importPage("museum-test", "glyph", 1, 0);

        assertEquals(1, result.signMappingProposals());
        verify(mappingPipeline).resolve(any());
        ArgumentCaptor<ResearchRecordRequest> captor = ArgumentCaptor.forClass(ResearchRecordRequest.class);
        verify(recordService).importRecord(captor.capture());
        assertEquals("SIGN_CANDIDATE", captor.getValue().recordType());
        assertEquals("PROPOSED_VALID", captor.getValue().provenance().path("signMappingAssessment").path("status").asText());
    }

    @Test
    void existingMuseumObjectIsReused() {
        ObjectNode raw = mapper.createObjectNode().put("id", 8);
        ResearchDiscoveryPreview.Candidate item = new ResearchDiscoveryPreview.Candidate("8", "Object",
                "https://example.org/8", List.of(), "UNKNOWN", raw);
        when(adapter.preview("seal", 1, 0)).thenReturn(new ResearchDiscoveryPreview("museum-test", "https://example.org/search", 1,
                List.of(item), mapper.createObjectNode(), "test"));
        when(recordService.importRecord(any())).thenReturn(new ResearchRecordResponse(8L, false, 0, 0));
        var result = service.importPage("museum-test", "seal", 1, 0);
        assertEquals(0, result.recordsCreated());
        assertEquals(1, result.recordsReused());
    }

    @Test
    void missingSourceIdentityIsSkipped() {
        var item = new ResearchDiscoveryPreview.Candidate("", "Unknown", "", List.of(), "UNKNOWN", mapper.createObjectNode());
        when(adapter.preview("x", 1, 0)).thenReturn(new ResearchDiscoveryPreview("museum-test", "https://example.org/search", 1,
                List.of(item), mapper.createObjectNode(), "test"));
        var result = service.importPage("museum-test", "x", 1, 0);
        assertEquals(1, result.skippedWithoutIdentity());
        verifyNoInteractions(recordService, mappingPipeline);
    }
}
