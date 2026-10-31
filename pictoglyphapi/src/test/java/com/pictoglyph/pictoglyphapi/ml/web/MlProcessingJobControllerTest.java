package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobProcessor;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.pictoglyph.pictoglyphapi.TestConstants.ABC_123_CHECKSUM;
import static com.pictoglyph.pictoglyphapi.TestConstants.SIGLIP_BASELINE_V1;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class MlProcessingJobControllerTest {

	@Mock
	private MlProcessingJobProcessor processor;

	@Mock
	private MlProcessingJobRepository repository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MlProcessingJobController controller = new MlProcessingJobController(processor, repository);

		mockMvc = MockMvcBuilders
				.standaloneSetup(controller)
				.build();
	}

	@Test
	void shouldProcessMlJob() throws Exception {
		MlProcessingJob completedJob = createCompletedJob();

		when(processor.processJob(25L)).thenReturn(completedJob);

		mockMvc.perform(post("/ml/jobs/25/process"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(25))
				.andExpect(jsonPath("$.symbolId").value(42))
				.andExpect(jsonPath("$.taskType").value("GENERATE_IMAGE_EMBEDDING"))
				.andExpect(jsonPath("$.modelProfile").value(SIGLIP_BASELINE_V1))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.attemptCount").value(1));

		verify(processor).processJob(25L);
	}

	@Test
	void shouldReturnMlJobStatus() throws Exception {
		MlProcessingJob completedJob = createCompletedJob();

		when(repository.findById(25L))
				.thenReturn(Optional.of(completedJob));

		mockMvc.perform(get("/ml/jobs/25"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(25))
				.andExpect(jsonPath("$.symbolId").value(42))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.attemptCount").value(1));

		verify(repository).findById(25L);
		verifyNoInteractions(processor);
	}

	@Test
	void shouldReturn404WhenJobDoesNotExist() throws Exception {
		when(repository.findById(999L))
				.thenReturn(Optional.empty());

		mockMvc.perform(get("/ml/jobs/999"))
				.andExpect(status().isNotFound());

		verifyNoInteractions(processor);
	}

	private MlProcessingJob createCompletedJob() {
		return MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
				.modelProfile(SIGLIP_BASELINE_V1)
				.status(MlProcessingStatus.COMPLETED)
				.attemptCount(1)
				.inputChecksum(ABC_123_CHECKSUM)
				.requestedAt(LocalDateTime.now().minusMinutes(1))
				.startedAt(LocalDateTime.now().minusSeconds(10))
				.completedAt(LocalDateTime.now())
				.build();
	}
}