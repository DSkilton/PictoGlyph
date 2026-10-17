package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(
		prefix = "pictoglyph.ml.worker",
		name = "enabled",
		havingValue = "true"
)
public class MlPendingJobWorker {

	private final MlProcessingJobRepository jobRepository;
	private final MlProcessingJobProcessor processor;
	private final int maxJobsPerStart;

	private int attemptedJobs = 0;

	MlPendingJobWorker(MlProcessingJobRepository jobRepository, MlProcessingJobProcessor processor,
					   @Value("${pictoglyph.ml.worker.max-jobs-per-start:1}") int maxJobsPerStart) {
		if (maxJobsPerStart < 1) {
			throw new IllegalArgumentException("Maximum ML jobs per start must be at least 1");
		}

		this.jobRepository = jobRepository;
		this.processor = processor;
		this.maxJobsPerStart = maxJobsPerStart;
	}

	@Scheduled(
			fixedDelayString = "${pictoglyph.ml.worker.poll-delay-ms:10000}",
			initialDelayString = "${pictoglyph.ml.worker.initial-delay-ms:15000}"
	)
	public void processNextPendingJob() {
		if (attemptedJobs >= maxJobsPerStart) {
			return;
		}

		Optional<MlProcessingJob> pendingJob = jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING);

		if (pendingJob.isEmpty()) {
			return;
		}

		Long jobId = pendingJob.get().getId();

		attemptedJobs++;

		try {
			log.info("Starting automatic ML job {} ({}/{})", jobId, attemptedJobs, maxJobsPerStart);

			MlProcessingJob result = processor.processJob(jobId);

			log.info("ML job {} finished with status {}", jobId, result.getStatus());
		} catch (RuntimeException e) {
			log.error("Automatic ML processing failed for job {}", jobId, e);
		}
	}
}
