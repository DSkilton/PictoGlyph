package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueItemResponse;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.pictoglyph.pictoglyphapi.TestConstants.ANCIENT_SCRIPTS_PILOT;

@ExtendWith(MockitoExtension.class)
public class DatasetSourceQueueControllerTest {

	@Mock
	private DatasetSourceQueueService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DatasetSourceQueueController controller = new DatasetSourceQueueController(service);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldRunDatasetSourceQueue() throws Exception {
		DatasetPreparationResponse dataset = new DatasetPreparationResponse(1L, ANCIENT_SCRIPTS_PILOT, DatasetReadinessStatus.REVIEW_REQUIRED, "2 ingestion item(s) require human review", null, null, null, null, null, null, null);
		DatasetSourceQueueItemResponse mayaResponse = new DatasetSourceQueueItemResponse(0, "Maya source", "https://example.org/maya", IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING, 100L, 10, 0, 2, null);
		DatasetSourceQueueItemResponse egyptianResponse = new DatasetSourceQueueItemResponse(1, "Egyptian source", "https://example.org/egyptian", IngestionStatus.COMPLETED, 101L, 10, 0, 0, null);

		DatasetSourceQueueResponse response = new DatasetSourceQueueResponse(dataset, 2, 2, 0, List.of(mayaResponse, egyptianResponse));

		when(service.run(ArgumentMatchers.any())).thenReturn(response);

		mockMvc.perform(
						post("/datasets/source-queues")
								.contentType("application/json")
								.content("""
										{
										  "datasetName": "Ancient scripts pilot",
										  "sources": [
										    {
										      "languageId": 1,
										      "sourceName": "Maya source",
										      "apiUrl": "https://example.org/maya",
										      "sourceFieldMapping": {
										        "itemArrayField": "symbols",
										        "symbolCodeField": "symbolCode",
										        "imagePathField": "imageUrl",
										        "titleField": "label",
										        "descriptionField": null,
										        "placeField": null,
										        "periodField": null,
										        "dateStartField": null,
										        "dateEndField": null
										      }
										    }
										  ]
										}
										""")

				)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.dataset.id").value(1))
				.andExpect(jsonPath("$.dataset.status").value("REVIEW_REQUIRED"))
				.andExpect(jsonPath("$.sourceCount").value(2))
				.andExpect(jsonPath("$.failedSourceCount").value(0))
				.andExpect(jsonPath("$.sources[0].sourceName").value("Maya source"))
				.andExpect(jsonPath("$.sources[1].sourceName").value("Egyptian source"));

	}
}
