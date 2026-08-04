package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlProcessingJobQueueServiceTest {

	private static final String MODEL_PROFILE = "SIGLIP_BASELINE_V1";

	@Mock
	private MlProcessingJobRepository repository;

	private MlProcessingJobQueueService service;

	@BeforeEach
	void setUp() {
		service = new MlProcessingJobQueueService(repository);
	}

	@Test
	void shouldCreatePendingImageEmbeddingJob() {
		when(repository
				.findFirstBySymbolIdAndTaskTypeAndModelProfileAndStatusInOrderByRequestedAtDesc(
						eq(42L),
						eq(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING),
						eq(MODEL_PROFILE),
						any(Collection.class)
				))
				.thenReturn(Optional.empty());

		when(repository.save(any(MlProcessingJob.class))).thenAnswer(invocation -> {
			MlProcessingJob job = invocation.getArgument(0);
			job.setId(100L);
			return job;
		});

		MlJobQueueResult result = service.queueImageEmbedding(42L, " SIGLIP_BASELINE_V1 ", " abc123 ");

		assertThat(result.created()).isTrue();
		assertThat(result.job().getId()).isEqualTo(100L);

		ArgumentCaptor<MlProcessingJob> captor = ArgumentCaptor.forClass(MlProcessingJob.class);

		verify(repository).save(captor.capture());

		MlProcessingJob savedJob = captor.getValue();

		assertThat(savedJob.getSymbolId()).isEqualTo(42L);
		assertThat(savedJob.getTaskType()).isEqualTo(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING);
		assertThat(savedJob.getModelProfile()).isEqualTo(MODEL_PROFILE);
		assertThat(savedJob.getInputChecksum()).isEqualTo("abc123");
		assertThat(savedJob.getStatus()).isEqualTo(MlProcessingStatus.PENDING);
		assertThat(savedJob.getAttemptCount()).isZero();
	}

	@Test
	void shouldReturnExistingJobWithoutCreatingDuplicate() {
		MlProcessingJob existingJob =
				MlProcessingJob.builder()
						.id(81L)
						.symbolId(42L)
						.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
						.modelProfile(MODEL_PROFILE).status(MlProcessingStatus.COMPLETED)
						.build();

		when(repository.findFirstBySymbolIdAndTaskTypeAndModelProfileAndStatusInOrderByRequestedAtDesc(eq(42L), eq(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING), eq(MODEL_PROFILE), any(Collection.class))).thenReturn(Optional.of(existingJob));

		MlJobQueueResult result = service.queueImageEmbedding(42L, MODEL_PROFILE, null);

		assertThat(result.created()).isFalse();
		assertThat(result.job()).isSameAs(existingJob);

		verify(repository, never()).save(any(MlProcessingJob.class));
	}

	@Test
	void shouldRejectMissingModelProfile() {
		assertThatThrownBy(() ->
				service.queueImageEmbedding(
						42L,
						" ",
						null
				)
		)
				.isInstanceOf(
						IllegalArgumentException.class
				)
				.hasMessageContaining(
						"model profile is required"
				);

		verify(repository, never()).save(any(MlProcessingJob.class));
	}

	@Test
	void shouldRejectInvalidSymbolId() {
		assertThatThrownBy(() ->
				service.queueImageEmbedding(
						0L,
						MODEL_PROFILE,
						null
				)
		)
				.isInstanceOf(
						IllegalArgumentException.class
				)
				.hasMessageContaining(
						"valid symbol id"
				);

		verify(repository, never()).save(any(MlProcessingJob.class));
	}

	@Test
	void shouldRejectMissingTaskType() {
		assertThatThrownBy(() ->
				service.queue(
						42L,
						null,
						MODEL_PROFILE,
						null
				)
		)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("task type is required");

		verify(repository, never()).save(any(MlProcessingJob.class));
	}
}