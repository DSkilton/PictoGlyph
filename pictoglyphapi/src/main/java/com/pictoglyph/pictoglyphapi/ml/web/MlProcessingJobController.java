package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ml/jobs")
@RequiredArgsConstructor
public class MlProcessingJobController {

	private final MlProcessingJobProcessor processor;

	@PostMapping("/{jobId}/process")
	public MlProcessingJobResponse processJob(@PathVariable Long jobId) {
		MlProcessingJob job = processor.processJob(jobId);

		return MlProcessingJobResponse.from(job);
	}
}
