package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobProcessor;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ml/jobs")
@RequiredArgsConstructor
public class MlProcessingJobController {

	private static final Logger LOGGER = LoggerFactory.getLogger(MlProcessingJobController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final MlProcessingJobProcessor processor;
	private final MlProcessingJobRepository repository;

	@GetMapping("/{jobId}")
	public MlProcessingJobResponse getJob(@PathVariable Long jobId) {
		LOGGER.debug("Retrieving ML processing job {}", jobId);

		MlProcessingJob job = repository.findById(jobId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ML job not found: " + jobId));

		return MlProcessingJobResponse.from(job);
	}

	@PostMapping("/{jobId}/process")
	public MlProcessingJobResponse processJob(@PathVariable Long jobId) {
		LOGGER.info("Starting ML job processing: jobId={}", jobId);

		MlProcessingJob job;

		try {
			job = processor.processJob(jobId);
		} catch (RuntimeException e) {
			LOGGER.warn("ML job processing failed: jobId={}, errorType={}", jobId, e.getClass().getSimpleName());
			throw e;
		}

		if (job.getStatus() == MlProcessingStatus.COMPLETED) {
			LOGGER.info("ML job processing completed: jobId={}, symbolId={}, status={}", job.getId(), job.getSymbolId(), job.getStatus());
		} else {
			LOGGER.warn("ML job processing finished without completion: jobId={}, symbolId={}, status={}", job.getId(), job.getSymbolId(), job.getStatus());
		}

		PROVENANCE_LOGGER.info("event=ml_job_processing_finished jobId={} symbolId={} taskType={} modelProfile={} status={} attempts={}", job.getId(), job.getSymbolId(), job.getTaskType(), job.getModelProfile(), job.getStatus(), job.getAttemptCount());

		return MlProcessingJobResponse.from(job);
	}
}
