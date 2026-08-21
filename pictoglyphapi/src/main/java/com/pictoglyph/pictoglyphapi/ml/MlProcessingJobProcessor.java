package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResultStatus;
import com.pictoglyph.pictoglyphapi.ml.client.MlProcessingClient;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MlProcessingJobProcessor {

	private final MlProcessingJobRepository jobRepository;
	private final SymbolRepository symbolRepository;
	private final MlProcessingRequestFactory requestFactory;
	private final MlProcessingClient processingClient;
	private final MlEmbeddingPersistenceService embeddingPersistenceService;

	public MlProcessingJob processJob(Long jobId) {
		if (jobId == null || jobId <= 0) {
			throw new IllegalArgumentException("A valid Ml processing job is required");
		}

		MlProcessingJob job = jobRepository.findById(jobId)
				.orElseThrow(() ->
						new IllegalArgumentException("No ml processing job found for id: " + jobId)
				);

		if (job.getStatus() != MlProcessingStatus.PENDING) {
			throw new IllegalStateException("Only pending Ml jobs can be processing");
		}

		job.markProcessing();
		jobRepository.save(job);

		try {
			process(job);
		} catch (RuntimeException e) {
			failProcessingJob(job, e);
		}

		return jobRepository.save(job);
	}

	private void process(MlProcessingJob job) {
		Symbol symbol = symbolRepository.findById(job.getSymbolId())
				.orElseThrow(() ->
						new IllegalStateException("No symbol found for Ml job symbol id: " + job.getSymbolId())
				);

		MlProcessingRequest request = requestFactory.create(job, symbol);
		MlProcessingResponse response = processingClient.process(request);

		if (response.status() == MlProcessingResultStatus.FAILED) {
			job.markFailed(response.errorMessage());
			return;
		}

		if (response.status() != MlProcessingResultStatus.COMPLETED) {
			throw new IllegalStateException("Ml service returned an unsupported processing status");
		}

		embeddingPersistenceService.persist(job, response);
		job.markCompleted();
	}

	private void failProcessingJob(MlProcessingJob job, RuntimeException e) {
		if (job.getStatus() == MlProcessingStatus.PROCESSING) {
			job.markFailed(e.getMessage());
		}
	}
}
