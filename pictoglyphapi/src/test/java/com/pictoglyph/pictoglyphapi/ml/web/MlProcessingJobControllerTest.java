package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static com.pictoglyph.pictoglyphapi.constants.ABC_123_CHECKSUM;
import static com.pictoglyph.pictoglyphapi.constants.SIGLIP_BASELINE_V_1;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class MlProcessingJobControllerTest {

	@Mock
	private MlProcessingJobProcessor processor;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MlProcessingJobController controller = new MlProcessingJobController(processor);
		mockMvc = MockMvcBuilders
				.standaloneSetup(controller)
				.build();
	}

	@Test
	void shouldProcessMlJob() throws Exception {
		MlProcessingJob completedJob = MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
				.modelProfile(SIGLIP_BASELINE_V_1)
				.status(MlProcessingStatus.COMPLETED)
				.attemptCount(1)
				.inputChecksum(ABC_123_CHECKSUM)
				.requestedAt(LocalDateTime.now().minusMinutes(1))
				.startedAt(LocalDateTime.now().minusSeconds(10))
				.completedAt(LocalDateTime.now())
				.build();

		when(processor.processJob(25L)).thenReturn(completedJob);
		mockMvc.perform(post("/ml/jobs/25/process"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(25))
				.andExpect(jsonPath("$.symbolId").value(42))
				.andExpect(jsonPath("$.taskType").value("GENERATE_IMAGE_EMBEDDING"))
				.andExpect(jsonPath("$.modelProfile").value(SIGLIP_BASELINE_V_1))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.attemptCount").value(1));
	}
}
