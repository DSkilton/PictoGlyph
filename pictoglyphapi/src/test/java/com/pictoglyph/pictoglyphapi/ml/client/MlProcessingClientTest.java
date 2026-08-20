package com.pictoglyph.pictoglyphapi.ml.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.api.MlApiContractVersions;
import com.pictoglyph.pictoglyphapi.ml.api.MlModelResult;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlProcessingClientTest {

	private static final String PROCESSING_URL = "http://127.0.0.1:8001/v1/process";

	@Mock
	private RestTemplate restTemplate;

	private MlProcessingClient client;

	@BeforeEach
	void setUp() {
		client = new MlProcessingClient(
				restTemplate,
				PROCESSING_URL
		);
	}

	@Test
	void shouldReturnSuccessfulMlResponse() {
		MlProcessingRequest request = createRequest();
		MlProcessingResponse response = createSuccessfulResponse();

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenReturn(response);

		MlProcessingResponse result = client.process(request);

		assertThat(result).isSameAs(response);
		assertThat(result.status()).isEqualTo(MlProcessingResultStatus.COMPLETED);
		assertThat(result.modelResults()).hasSize(1);
	}

	@Test
	void shouldAllowFailedProcessingResponse() {
		MlProcessingRequest request = createRequest();

		MlProcessingResponse response = new MlProcessingResponse(MlApiContractVersions.V1, 25L, 42L, MlProcessingResultStatus.FAILED, List.of(), Instant.parse("2026-08-20T12:00:00Z"), "Image could not be opened");

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenReturn(response);

		MlProcessingResponse result = client.process(request);

		assertThat(result.status()).isEqualTo(MlProcessingResultStatus.FAILED);
		assertThat(result.errorMessage()).isEqualTo("Image could not be opened");
	}

	@Test
	void shouldRejectResponseForDifferentJob() {
		MlProcessingRequest request = createRequest();

		MlProcessingResponse response = new MlProcessingResponse(
						MlApiContractVersions.V1,
						999L,
						42L,
						MlProcessingResultStatus.COMPLETED,
						List.of(),
						Instant.now(),
						null
				);

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenReturn(response);

		assertThatThrownBy(() ->
				client.process(request)
		)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("job id");
	}

	@Test
	void shouldRejectResponseForDifferentSymbol() {
		MlProcessingRequest request = createRequest();

		MlProcessingResponse response = new MlProcessingResponse(
						MlApiContractVersions.V1,
						25L,
						999L,
						MlProcessingResultStatus.COMPLETED,
						List.of(),
						Instant.now(),
						null
				);

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenReturn(response);

		assertThatThrownBy(() ->
				client.process(request)
		)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("symbol id");
	}

	@Test
	void shouldRejectEmptyResponse() {
		MlProcessingRequest request = createRequest();

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenReturn(null);

		assertThatThrownBy(() ->
				client.process(request)
		)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("empty response");
	}

	@Test
	void shouldWrapConnectionFailure() {
		MlProcessingRequest request = createRequest();

		when(restTemplate.postForObject(PROCESSING_URL, request, MlProcessingResponse.class))
				.thenThrow(new ResourceAccessException("Connection refused")
		);

		assertThatThrownBy(() ->
				client.process(request)
		)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not call Ml processing service")
				.hasRootCauseInstanceOf(ResourceAccessException.class);
	}

	private MlProcessingRequest createRequest() {
		return new MlProcessingRequest(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingTaskType
						.GENERATE_IMAGE_EMBEDDING,
				"SIGLIP_BASELINE_V1",
				"..\\pictoglyph\\test_api\\A1.png",
				"abc123checksum",
				"A1",
				1L,
				new ObjectMapper()
						.createObjectNode()
						.put("sourceType", "API")
		);
	}

	private MlProcessingResponse
	createSuccessfulResponse() {
		MlModelResult modelResult = new MlModelResult(
						"siglip2",
						"mock-v1",
						3,
						List.of(
								0.12,
								-0.04,
								0.31
						),
						new ObjectMapper()
								.createObjectNode()
								.put("mock", true)
				);

		return new MlProcessingResponse(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingResultStatus.COMPLETED,
				List.of(modelResult),
				Instant.parse(
						"2026-08-20T12:00:00Z"
				),
				null
		);
	}
}