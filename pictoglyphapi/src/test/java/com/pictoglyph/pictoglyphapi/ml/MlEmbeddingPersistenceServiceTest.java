package com.pictoglyph.pictoglyphapi.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.ml.MlEmbedding;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.api.MlApiContractVersions;
import com.pictoglyph.pictoglyphapi.ml.api.MlModelResult;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResultStatus;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MlEmbeddingPersistenceServiceTest {

	@Mock
	private MlEmbeddingRepository repository;

	private MlEmbeddingPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new MlEmbeddingPersistenceService(
				repository,
				new ObjectMapper()
		);
	}

	@Test
	void shouldPersistCompletedEmbedding() {
		MlProcessingJob job = MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
				.modelProfile("SIGLIP_BASELINE_V1")
				.inputChecksum("abc123checksum")
				.build();

		MlModelResult modelResult = new MlModelResult(
				"siglip2",
				"mock-v1",
				3,
				List.of(0.12, -0.04, 0.31),
				new ObjectMapper()
						.createObjectNode()
						.put("normalisation", "l2")
		);

		MlProcessingResponse response = new MlProcessingResponse(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingResultStatus.COMPLETED,
				List.of(modelResult),
				Instant.parse(
						"2026-08-20T18:30:00Z"
				),
				null
		);

		when(repository.findByProcessingJobIdAndModelNameAndModelVersion(25L, "siglip2", "mock-v1"))
				.thenReturn(Optional.empty());

		when(repository.save(any(MlEmbedding.class))).thenAnswer(invocation ->
				invocation.getArgument(0)
		);

		List<MlEmbedding> result = service.persist(job, response);

		assertThat(result).hasSize(1);

		MlEmbedding embedding = result.get(0);

		assertThat(embedding.getProcessingJobId()).isEqualTo(25L);
		assertThat(embedding.getSymbolId()).isEqualTo(42L);
		assertThat(embedding.getModelName()).isEqualTo("siglip2");
		assertThat(embedding.getModelVersion()).isEqualTo("mock-v1");
		assertThat(embedding.getModelProfile()).isEqualTo("SIGLIP_BASELINE_V1");
		assertThat(embedding.getEmbeddingDimension()).isEqualTo(3);
		assertThat(embedding.getEmbedding().size()).isEqualTo(3);
		assertThat(embedding.getInputChecksum()).isEqualTo("abc123checksum");
		assertThat(embedding
				.getPreprocessing()
				.path("normalisation")
				.asText()
		).isEqualTo("l2");
	}
}
