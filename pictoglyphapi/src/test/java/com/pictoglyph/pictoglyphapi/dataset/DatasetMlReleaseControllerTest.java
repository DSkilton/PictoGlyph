package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DatasetMlReleaseControllerTest {

	private static final Long DATASET_ID = 4L;
	private static final String MODEL_PROFILE = "SIGLIP_BASELINE_V1";

	@Mock
	private DatasetMlReleaseService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DatasetMlReleaseController controller = new DatasetMlReleaseController(service);

		mockMvc = MockMvcBuilders
				.standaloneSetup(controller)
				.build();
	}

	@Test
	void shouldReleaseDatasetToMl() throws Exception {
		ReleaseDatasetToMlResponse response = new ReleaseDatasetToMlResponse(DATASET_ID, MODEL_PROFILE, 25 , 25, 0);

		when(service.release(DATASET_ID, MODEL_PROFILE)).thenReturn(response);

		mockMvc.perform(post("/datasets/preparations/{datasetPreparationId}/release-to-ml", DATASET_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "modelProfile": "SIGLIP_BASELINE_V1"
								}
								""")
				)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.datasetPreparationId").value(4))
				.andExpect(jsonPath("$.modelProfile").value("SIGLIP_BASELINE_V1"))
				.andExpect(jsonPath("$.symbolCount").value(25))
				.andExpect(jsonPath("$.createdJobCount").value(25))
				.andExpect(jsonPath("$.existingJobCount").value(0));

		verify(service).release(DATASET_ID, MODEL_PROFILE);
	}
}