
package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MlPendingJobWorkerTest {

	@Mock
	private MlProcessingJobRepository jobRepository;

	@Mock
	private MlProcessingJobProcessor processor;

	private MlPendingJobWorker worker;

	@BeforeEach
	void setUp() {
		worker = new MlPendingJobWorker(jobRepository, processor, 1);
	}

	@Test
	void shouldStopAfterMaximumJobsPerStart() {
		MlProcessingJob pending = MlProcessingJob.builder()
				.id(5L)
				.status(MlProcessingStatus.PENDING)
				.build();

		MlProcessingJob completed = MlProcessingJob.builder()
				.id(5L)
				.status(MlProcessingStatus.COMPLETED)
				.build();

		when(jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING)).thenReturn(Optional.of(pending));
		when(processor.processJob(5L)).thenReturn(completed);

		worker.processNextPendingJob();
		worker.processNextPendingJob();

		verify(jobRepository, times(1)).findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING);
		verify(processor, times(1)).processJob(5L);
	}

	@Test
	void shouldDoNothingWhenNoJobsArePending() {
		when(jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING)).thenReturn(Optional.empty());
		worker.processNextPendingJob();
		verifyNoInteractions(processor);
	}

	@Test
	void shouldProcessOnlyOnePendingJob() {
		MlProcessingJob pending = MlProcessingJob.builder()
				.id(5L)
				.status(MlProcessingStatus.PENDING)
				.build();

		MlProcessingJob completed = MlProcessingJob.builder()
				.id(5L)
				.status(MlProcessingStatus.COMPLETED)
				.build();

		when(jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING)).thenReturn(Optional.of(pending));
		when(processor.processJob(5L)).thenReturn(completed);

		worker.processNextPendingJob();

		verify(processor).processJob(5L);
		verifyNoMoreInteractions(processor);
	}

	@Test
	void shouldHandleProcessingFailure() {
		MlProcessingJob pending = MlProcessingJob.builder()
				.id(5L)
				.status(MlProcessingStatus.PENDING)
				.build();

		when(jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING)).thenReturn(Optional.of(pending));
		when(processor.processJob(5L)).thenThrow(new IllegalStateException("ML service unavailable"));

		assertThatCode(() -> worker.processNextPendingJob()).doesNotThrowAnyException();
		verify(processor).processJob(5L);
	}

	@Test
	void shouldUseConfigurableScheduling() throws NoSuchMethodException {
		Scheduled scheduled = MlPendingJobWorker.class
				.getMethod("processNextPendingJob")
				.getAnnotation(Scheduled.class);

		assertThat(scheduled.fixedDelayString()).isEqualTo("${pictoglyph.ml.worker.poll-delay-ms:10000}");
		assertThat(scheduled.initialDelayString()).isEqualTo("${pictoglyph.ml.worker.initial-delay-ms:15000}");
	}
}
