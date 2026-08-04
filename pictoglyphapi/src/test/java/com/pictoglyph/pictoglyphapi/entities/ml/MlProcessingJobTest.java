package com.pictoglyph.pictoglyphapi.entities.ml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlProcessingJobTest {

	@Test
	void shouldMoveFromPendingToProcessingAndCompleted() {
		MlProcessingJob job = job();

		job.onCreate();

		assertThat(job.getStatus()).isEqualTo(MlProcessingStatus.PENDING);

		assertThat(job.getAttemptCount()).isZero();
		assertThat(job.getRequestedAt()).isNotNull();

		job.markProcessing();

		assertThat(job.getStatus()).isEqualTo(MlProcessingStatus.PROCESSING);

		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getStartedAt()).isNotNull();
		assertThat(job.getLastError()).isNull();

		job.markCompleted();

		assertThat(job.getStatus()).isEqualTo(MlProcessingStatus.COMPLETED);

		assertThat(job.getCompletedAt()).isNotNull();
		assertThat(job.getLastError()).isNull();
	}

	@Test
	void shouldRecordFailureAndResetForRetry() {
		MlProcessingJob job = job();

		job.markProcessing();
		job.markFailed(" Python service was unavailable ");

		assertThat(job.getStatus()).isEqualTo(MlProcessingStatus.FAILED);

		assertThat(job.getAttemptCount()).isEqualTo(1);

		assertThat(job.getLastError()).isEqualTo("Python service was unavailable");

		assertThat(job.getCompletedAt()).isNotNull();

		job.resetForRetry();

		assertThat(job.getStatus()).isEqualTo(MlProcessingStatus.PENDING);

		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getStartedAt()).isNull();
		assertThat(job.getCompletedAt()).isNull();
		assertThat(job.getLastError()).isNull();

		job.markProcessing();

		assertThat(job.getAttemptCount()).isEqualTo(2);
	}

	@Test
	void shouldUseFallbackMessageForBlankFailure() {
		MlProcessingJob job = job();

		job.markProcessing();
		job.markFailed(" ");

		assertThat(job.getLastError()).isEqualTo("Unknown ML processing error");
	}

	@Test
	void shouldRejectCompletionBeforeProcessing() {
		MlProcessingJob job = job();

		assertThatThrownBy(job::markCompleted)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(
						"must be processing"
				);
	}

	@Test
	void shouldRejectResettingJobThatHasNotFailed() {
		MlProcessingJob job = job();

		assertThatThrownBy(job::resetForRetry)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(
						"Only failed ML jobs"
				);
	}

	@Test
	void shouldNotCancelCompletedJob() {
		MlProcessingJob job = job();

		job.markProcessing();
		job.markCompleted();

		assertThatThrownBy(job::cancel)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(
						"Completed ML jobs cannot be cancelled"
				);
	}

	private MlProcessingJob job() {
		return MlProcessingJob.builder()
				.symbolId(42L)
				.taskType(
						MlProcessingTaskType
								.GENERATE_IMAGE_EMBEDDING
				)
				.modelProfile("SIGLIP_BASELINE_V1")
				.inputChecksum("abc123")
				.build();
	}
}