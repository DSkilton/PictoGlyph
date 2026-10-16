package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Comment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		prefix = "pictoglyph.ml.worker",
		name = "enabled",
		havingValue = "true"
)
public class MlPendingJobWorker {

	private final MlProcessingJobRepository jobRepository;
	private final MlProcessingJobProcessor processor;

	@Scheduled(
			fixedDelayString = "$(pictoglyph.ml.worker.poll-delay-ms:10000}",
			initialDelayString = "${pictoglpy.ml.worker.initial-delay-ms:15000}"
	)
	public void processNextPendingJob() {
		Optional<MlProcessingJob> pendingJob = jobRepository.findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus.PENDING);

		if (pendingJob.isEmpty()) {
			return;
		}

		Long jobId = pendingJob.get().getId();

		try {
			log.info("Starting automatic ML processing for job {}", jobId);

			MlProcessingJob result = processor.processJob(jobId);

			log.info("ML job {} finished with status {}", jobId, result.getStatus());
		} catch (RuntimeException e) {
			log.error("Automatic ML processing failed for job {}", jobId, e);
		}
	}
}
