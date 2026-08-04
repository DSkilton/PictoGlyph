package com.pictoglyph.pictoglyphapi.ml.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlProcessingContractTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules();
	}

	@Test
	void shouldSerialiseProcessingRequest() throws Exception {
		JsonNode metadata = JsonNodeFactory.instance.objectNode()
						.put("sourceType", "API")
						.put("sourceName", "Cleveland Museum of Art");

		MlProcessingRequest request = new MlProcessingRequest(
						MlApiContractVersions.V1,
						25L,
						42L,
						MlProcessingTaskType
								.GENERATE_IMAGE_EMBEDDING,
						"SIGLIP_BASELINE_V1",
						"C:\\pictoglyph\\A1.png",
						"abc123checksum",
						"A1",
						1L,
						metadata
				);

		JsonNode json = objectMapper.valueToTree(request);

		assertThat(json.path("contractVersion").asText()).isEqualTo("1.0");
		assertThat(json.path("jobId").asLong()).isEqualTo(25L);
		assertThat(json.path("symbolId").asLong()).isEqualTo(42L);
		assertThat(json.path("taskType").asText()).isEqualTo("GENERATE_IMAGE_EMBEDDING");
		assertThat(json.path("modelProfile").asText()).isEqualTo("SIGLIP_BASELINE_V1");
		assertThat(json.path("inputChecksum").asText()).isEqualTo("abc123checksum");

		assertThat(json.path("metadata").path("sourceName").asText()).isEqualTo("Cleveland Museum of Art");
	}

	@Test
	void shouldDefaultMissingRequestMetadataToEmptyObject() {
		MlProcessingRequest request = new MlProcessingRequest(
						MlApiContractVersions.V1,
						25L,
						42L,
						MlProcessingTaskType
								.GENERATE_IMAGE_EMBEDDING,
						"SIGLIP_BASELINE_V1",
						"C:\\pictoglyph\\A1.png",
						"abc123checksum",
						"A1",
						1L,
						null
				);

		assertThat(request.metadata().isObject()).isTrue();
		assertThat(request.metadata().isEmpty()).isTrue();
	}

	@Test
	void shouldDeserialiseSuccessfulPythonResponse() throws Exception {

		String responseJson = """
				{
				  "contractVersion": "1.0",
				  "jobId": 25,
				  "symbolId": 42,
				  "status": "COMPLETED",
				  "modelResults": [
				    {
				      "modelName": "siglip2",
				      "modelVersion": "google/siglip2-base-patch16-naflex",
				      "embeddingDimension": 3,
				      "embedding": [
				        0.12,
				        -0.04,
				        0.31
				      ],
				      "preprocessing": {
				        "normalisation": "model-default",
				        "device": "cuda:0"
				      }
				    }
				  ],
				  "processedAt": "2026-08-04T20:45:00Z",
				  "errorMessage": null,
				  "futureField": "ignored"
				}
				""";

		MlProcessingResponse response = objectMapper.readValue(responseJson, MlProcessingResponse.class);

		assertThat(response.contractVersion()).isEqualTo(MlApiContractVersions.V1);
		assertThat(response.jobId()).isEqualTo(25L);
		assertThat(response.symbolId()).isEqualTo(42L);
		assertThat(response.status()).isEqualTo(MlProcessingResultStatus.COMPLETED);

		assertThat(response.processedAt()).isEqualTo(Instant.parse("2026-08-04T20:45:00Z"));
		assertThat(response.errorMessage()).isNull();

		assertThat(response.modelResults())
				.singleElement()
				.satisfies(result -> {
					assertThat(result.modelName())
							.isEqualTo("siglip2");

					assertThat(result.modelVersion())
							.isEqualTo(
									"google/siglip2-base-patch16-naflex"
							);

					assertThat(result.embeddingDimension())
							.isEqualTo(3);

					assertThat(result.embedding())
							.containsExactly(
									0.12,
									-0.04,
									0.31
							);

					assertThat(
							result.preprocessing()
									.path("device")
									.asText()
					).isEqualTo("cuda:0");
				});
	}

	@Test
	void shouldDeserialiseFailedPythonResponse() throws Exception {

		String responseJson = """
				{
				  "contractVersion": "1.0",
				  "jobId": 25,
				  "symbolId": 42,
				  "status": "FAILED",
				  "modelResults": [],
				  "processedAt": "2026-08-04T20:45:00Z",
				  "errorMessage": "Image could not be opened"
				}
				""";

		MlProcessingResponse response =
				objectMapper.readValue(
						responseJson,
						MlProcessingResponse.class
				);

		assertThat(response.status()).isEqualTo(MlProcessingResultStatus.FAILED);
		assertThat(response.modelResults()).isEmpty();
		assertThat(response.errorMessage()).isEqualTo("Image could not be opened");
	}

	@Test
	void shouldDefensivelyCopyModelResults() {
		List<Double> embedding = new java.util.ArrayList<>(List.of(0.1, 0.2));

		MlModelResult result = new MlModelResult("siglip2", "model-v1", 2, embedding, null);

		embedding.add(0.3);

		assertThat(result.embedding()).containsExactly(0.1, 0.2);

		assertThat(result.preprocessing().isObject()).isTrue();
	}
}