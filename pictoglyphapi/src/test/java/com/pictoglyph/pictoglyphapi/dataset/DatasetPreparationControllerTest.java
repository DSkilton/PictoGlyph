package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DatasetPreparationControllerTest {

	@Mock
	private DatasetPreparationService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DatasetPreparationController controller = new DatasetPreparationController(service);

		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldCreateDatasetPreparation() throws Exception {
		DatasetPreparationResponse response = new DatasetPreparationResponse(
						1L,
						"Ancient scripts pilot",
						DatasetReadinessStatus.INGESTING,
						"Dataset sources are being ingested",
						null,
						null,
						null,
						null,
						null,
						null,
						null
				);

		when(service.create("Ancient scripts pilot")).thenReturn(response);

		mockMvc.perform(post("/datasets/preparations")
								.contentType("application/json")
								.content("""
                                        {
                                          "name": "Ancient scripts pilot"
                                        }
                                        """)
				)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Ancient scripts pilot"))
				.andExpect(jsonPath("$.status").value("INGESTING")
				);
	}

	@Test
	void shouldInitialiseAuditTimestampsBeforePersistence() {
		DatasetPreparation preparation = DatasetPreparation.builder()
						.name("Ancient Scripts Pilot")
						.build();

		preparation.onCreate();

		assertThat(preparation.getCreatedAt()).isNotNull();
		assertThat(preparation.getUpdatedAt()).isNotNull();
		assertThat(preparation.getStatus()).isEqualTo(DatasetReadinessStatus.INGESTING);
	}
}