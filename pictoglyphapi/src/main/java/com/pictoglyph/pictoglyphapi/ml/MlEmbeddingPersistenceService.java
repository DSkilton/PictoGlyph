package com.pictoglyph.pictoglyphapi.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.ml.MlEmbedding;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.ml.api.MlModelResult;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResultStatus;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlEmbeddingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MlEmbeddingPersistenceService {

	private final MlEmbeddingRepository repository;
	private final ObjectMapper objectMapper;

	@Transactional
	public List<MlEmbedding> persist(MlProcessingJob job, MlProcessingResponse response) {
		validate(job, response);

		List<MlEmbedding> persistedEmbeddings = new ArrayList<>();

		for (MlModelResult result : response.modelResults()) {
			validateModelResult(result);

			MlEmbedding embedding = repository.findByProcessingJobIdAndModelNameAndModelVersion(job.getId(),result.modelName(), result.modelVersion())
					.orElseGet(() -> createEmbedding(job, response, result));

			persistedEmbeddings.add(embedding);
		}

		return List.copyOf(persistedEmbeddings);
	}

	private MlEmbedding createEmbedding(MlProcessingJob job, MlProcessingResponse response, MlModelResult result) {
		LocalDateTime processedAt = LocalDateTime.ofInstant(response.processedAt(), ZoneOffset.UTC);

		MlEmbedding embedding = MlEmbedding.builder()
				.processingJobId(job.getId())
				.symbolId(job.getSymbolId())
				.modelName(result.modelName())
				.modelVersion(result.modelVersion())
				.modelProfile(job.getModelProfile())
				.embeddingDimension(result.embeddingDimension())
				.embedding(objectMapper.valueToTree(result.embedding()))
				.preprocessing(result.preprocessing() == null
						? objectMapper.createObjectNode()
						: result.preprocessing().deepCopy())
				.inputChecksum(job.getInputChecksum())
				.processedAt(processedAt)
				.build();

			return repository.save(embedding);
	}

	private void validate(MlProcessingJob job, MlProcessingResponse response) {
		if (job == null) {
			throw new IllegalArgumentException("ML processing job is required");
		}

		if (job.getId() == null) {
			throw new IllegalArgumentException("ML processing job must have an id");
		}

		if (response == null) {
			throw new IllegalArgumentException("ML processing response is required");
		}

		if (response.status() != MlProcessingResultStatus.COMPLETED) {
			throw new IllegalArgumentException("Only completed ML responses can be persisted");
		}

		if (!job.getId().equals(response.jobId())) {
			throw new IllegalArgumentException("ML response job id does not match processing job");
		}

		if (!job.getSymbolId().equals(response.symbolId())) {
			throw new IllegalArgumentException("ML response symbol id does not match processing job");
		}

		if (job.getInputChecksum() == null || job.getInputChecksum().isBlank()) {
			throw new IllegalArgumentException("ML processing job requires an input checksum");
		}

		if (response.processedAt() == null) {
			throw new IllegalArgumentException("ML processing response requires a processed timestamp");
		}

		if (response.modelResults().isEmpty()) {
			throw new IllegalArgumentException("Completed ML response contains no model results");
		}
	}

	private void validateModelResult(MlModelResult result) {
		if (result == null) {
			throw new IllegalArgumentException("ML model result is required");
		}

		if (result.modelName() == null || result.modelName().isBlank()) {
			throw new IllegalArgumentException("ML model name is required");
		}

		if (result.modelVersion() == null || result.modelVersion().isBlank()) {
			throw new IllegalArgumentException("ML model version is required");
		}

		if (result.embeddingDimension() <= 0) {
			throw new IllegalArgumentException("ML embedding dimension must be greater than zero");
		}

		if (result.embedding() == null || result.embedding().size() != result.embeddingDimension()) {
			throw new IllegalArgumentException("ML embedding size does not match embedding dimension");
		}
	}

}
