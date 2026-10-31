package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobProcessor;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ml/jobs")
@RequiredArgsConstructor
public class MlProcessingJobController {

	private final MlProcessingJobProcessor processor;
	private final MlProcessingJobRepository repository;

	@GetMapping("/{jobId}")
	public MlProcessingJobResponse getJob(
			@PathVariable Long jobId) {

		MlProcessingJob job = repository.findById(jobId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"ML job not found: " + jobId
				));

		return MlProcessingJobResponse.from(job);
	}

	@PostMapping("/{jobId}/process")
	public MlProcessingJobResponse processJob(
			@PathVariable Long jobId) {

		MlProcessingJob job = processor.processJob(jobId);

		return MlProcessingJobResponse.from(job);
	}
}