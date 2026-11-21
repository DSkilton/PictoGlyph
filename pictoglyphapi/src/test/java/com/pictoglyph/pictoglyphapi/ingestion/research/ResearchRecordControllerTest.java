package com.pictoglyph.pictoglyphapi.ingestion.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResearchRecordControllerTest {
    @Mock
    private ResearchRecordService service;

    private MockMvc mvc;
    private ResearchRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new ResearchRecordController(service);
        ReflectionTestUtils.setField(controller, "enabled", true);
        ReflectionTestUtils.setField(controller, "importToken", "test-token");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldAttachCuratedAssetWithValidToken() throws Exception {
        when(service.attachImageAsset(eq("met-openaccess-metadata"), eq("object:550876"), any(ResearchImageAssetRequest.class)))
                .thenReturn(new ResearchRecordResponse(16234L, false, 1, 0));

        mvc.perform(post("/ingestion/research-records/met-openaccess-metadata/records/object:550876/image-assets")
                        .header("X-PictoGlyph-Import-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(imageJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordId").value(16234))
                .andExpect(jsonPath("$.imageAssetsCreated").value(1));
    }

    @Test
    void shouldReturnOkOnIdempotentAttachment() throws Exception {
        when(service.attachImageAsset(eq("met-openaccess-metadata"), eq("object:550876"), any(ResearchImageAssetRequest.class)))
                .thenReturn(new ResearchRecordResponse(16234L, false, 0, 0));

        mvc.perform(post("/ingestion/research-records/met-openaccess-metadata/records/object:550876/image-assets")
                        .header("X-PictoGlyph-Import-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(imageJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageAssetsCreated").value(0));
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        mvc.perform(post("/ingestion/research-records/met-openaccess-metadata/records/object:550876/image-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(imageJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void shouldRespectDisabledImportGate() throws Exception {
        ReflectionTestUtils.setField(controller, "enabled", false);
        mvc.perform(post("/ingestion/research-records/met-openaccess-metadata/records/object:550876/image-assets")
                        .header("X-PictoGlyph-Import-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(imageJson()))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }

    private String imageJson() {
        return """
                {
                  "assetKey": "commons:550876",
                  "sourcePath": "/mnt/i/PictoGlyph/commons-pilot/met-550876.jpg",
                  "imageKind": "LOCAL_RESEARCH_IMAGE",
                  "rights": "CC0-1.0",
                  "checksumSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                  "analysis": {"trainingDataApproved": false, "distributionSource": "Wikimedia Commons"}
                }
                """;
    }
}
