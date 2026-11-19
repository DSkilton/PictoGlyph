package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResearchExactObjectIdControllerTest {
    private static final String SOURCE_ID = "met-openaccess-metadata";
    private static final String TOKEN = "local-test-token";
    private static final String PREVIEW_ENDPOINT = "/ingestion/research-discovery/" + SOURCE_ID + "/preview-ids";
    private static final String IMPORT_ENDPOINT = "/ingestion/research-discovery/" + SOURCE_ID + "/import-ids";
    private final ResearchDiscoveryService service = mock(ResearchDiscoveryService.class);
    private MockMvc mvc;
    private ResearchDiscoveryController controller;

    @BeforeEach
    void setUp() {
        controller = new ResearchDiscoveryController(service);

        ReflectionTestUtils.setField(controller, "importToken", TOKEN);
        ReflectionTestUtils.setField(controller, "researchImportEnabled", false);
        ReflectionTestUtils.setField(controller, "discoveryImportEnabled", false);

        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void validPreviewCallsTheServiceWithoutEnablingImports() throws Exception {
        ResearchDiscoveryPreview preview = new ResearchDiscoveryPreview(SOURCE_ID, "https://collectionapi.metmuseum.org/public/collection/v1/objects/",
                1, List.of(), new ObjectMapper().createObjectNode(), "Exact-ID preview");

        when(service.previewObjectIds(SOURCE_ID, List.of(550876))).thenReturn(preview);

        mvc.perform(post(PREVIEW_ENDPOINT).header("X-PictoGlyph-Import-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceId").value(SOURCE_ID));

        verify(service).previewObjectIds(SOURCE_ID, List.of(550876));
    }

    @Test
    void missingAndIncorrectTokensAreRejected() throws Exception {
        mvc.perform(post(PREVIEW_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(IMPORT_ENDPOINT).header("X-PictoGlyph-Import-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void malformedOrDuplicateObjectIdsAreRejected() throws Exception {
        String[] invalidBodies = {"[]", "[0]", "[550876,550876]", "[1,2,3,4,5,6,7,8,9,10,11]"};

        for (String body : invalidBodies) {
            mvc.perform(post(PREVIEW_ENDPOINT).header("X-PictoGlyph-Import-Token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(service);
    }

    @Test
    void disabledImportIsRejectedWithServiceUnavailable() throws Exception {
        mvc.perform(post(IMPORT_ENDPOINT).header("X-PictoGlyph-Import-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(service);
    }

    @Test
    void enabledImportUsesExactIds() throws Exception {
        ReflectionTestUtils.setField(controller, "researchImportEnabled", true);
        ReflectionTestUtils.setField(controller, "discoveryImportEnabled", true);

        ResearchDiscoveryImportResult result = new ResearchDiscoveryImportResult(SOURCE_ID, "https://collectionapi.metmuseum.org/public/collection/v1/objects/",
                1, 1, 0, 2, 0, 0);

        when(service.importObjectIds(SOURCE_ID, List.of(550876))).thenReturn(result);

        mvc.perform(post(IMPORT_ENDPOINT).header("X-PictoGlyph-Import-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsCreated").value(1))
                .andExpect(jsonPath("$.imageReferencesCreated").value(2));

        verify(service).importObjectIds(SOURCE_ID, List.of(550876));
    }

    @Test
    void unsupportedSourceReturnsNotFound() throws Exception {
        when(service.previewObjectIds(SOURCE_ID, List.of(550876))).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Research source does not support exact object IDs"));

        mvc.perform(post(PREVIEW_ENDPOINT).header("X-PictoGlyph-Import-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("[550876]"))
                .andExpect(status().isNotFound());
    }
}
