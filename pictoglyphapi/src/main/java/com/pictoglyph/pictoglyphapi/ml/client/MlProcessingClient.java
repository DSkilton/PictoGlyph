package com.pictoglyph.pictoglyphapi.ml.client;

import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MlProcessingClient {

	private final RestTemplate restTemplate;
	private final String processingUrl;

	public MlProcessingClient(RestTemplate restTemplate, @Value("${pictoglyph.ml.processing-url}") String processingUrl) {
		this.restTemplate = restTemplate;
		this.processingUrl = requireProcessingUrl(processingUrl);
	}

	public MlProcessingResponse process(MlProcessingRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Ml processing request is required");
		}

		try {
			MlProcessingResponse response = restTemplate.postForObject(processingUrl, request, MlProcessingResponse.class);

			if (response == null) {
				throw new IllegalStateException("Ml service returned an empty response");
			}

			validateResponse(request, response);

			return response;
		} catch (HttpStatusCodeException e) {
			throw new IllegalStateException("Ml processing service returned HTTP " + e.getStatusCode().value());
		} catch (RestClientException e) {
			throw new IllegalStateException("Could not call Ml processing service", e);
		}
	}

	private String requireProcessingUrl(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Ml processing URL is required");
		}

		return value.trim();
	}

	private void validateResponse(MlProcessingRequest request, MlProcessingResponse response) {
		if (!request.contractVersion().equals(response.contractVersion())) {
			throw new IllegalStateException("Ml response contract does not match request contract version");
		}

		if (!request.jobId().equals(response.jobId())) {
			throw new IllegalStateException("ML response job id does not match request job id");
		}

		if (!request.symbolId().equals(response.symbolId())) {
			throw new IllegalStateException("Ml response symbol id does not match request symbol id");
		}
	}
}
