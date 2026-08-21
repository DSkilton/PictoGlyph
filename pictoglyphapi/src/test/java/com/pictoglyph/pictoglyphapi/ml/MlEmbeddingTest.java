package com.pictoglyph.pictoglyphapi.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.ml.MlEmbedding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MlEmbeddingTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldSetCreatedAtBeforePersistence() {
		MlEmbedding embedding = MlEmbedding.builder()
				.processingJobId(25L)
				.symbolId(42L)
				.modelName("siglip2")
				.modelVersion("mock-v1")
				.modelProfile("SIGLIP_BASELINE_V1")
				.embeddingDimension(3)
				.embedding(objectMapper.valueToTree(
						new double[]{0.12, -0.04, 0.31}
				))
				.inputChecksum("abc123checksum")
				.build();

		assertThat(embedding.getCreatedAt()).isNull();
		embedding.onCreate();
		assertThat(embedding.getCreatedAt()).isNotNull();
	}

	@Test
	void shouldRetainEmbeddingAndPreprocessingMetadata() {
		MlEmbedding embedding = MlEmbedding.builder()
						.processingJobId(25L)
						.symbolId(42L)
						.modelName("siglip2")
						.modelVersion("mock-v1")
						.modelProfile("SIGLIP_BASELINE_V1")
						.embeddingDimension(3)
						.embedding(
								objectMapper.valueToTree(
										new double[]{0.12, -0.04, 0.31}
								)
						)
						.preprocessing(objectMapper.createObjectNode()
										.put("normalisation", "l2")
						)
						.inputChecksum("abc123checksum")
						.build();

		assertThat(embedding.getEmbedding().size()).isEqualTo(3);
		assertThat(embedding.getPreprocessing().path("normalisation").asText()).isEqualTo("l2");
	}
}
