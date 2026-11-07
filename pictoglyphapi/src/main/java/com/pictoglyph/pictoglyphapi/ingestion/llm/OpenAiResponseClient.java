package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OpenAiResponseClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiResponseClient.class);
	private static final String API_URL = "https://api.openai.com/v1/responses";

	private static final int MAX_OUTPUT_TOKENS = 128;

	private final RestTemplate restTemplate;
	private final String apiKey;
	private final String model;
	private final boolean enabled;

	public OpenAiResponseClient(
			@Qualifier("openAiRestTemplate") RestTemplate restTemplate,
			@Value("${OPENAI_API_KEY:}") String apiKey,
			@Value("${pictoglyph.openai.model}") String model,
			@Value("${pictoglyph.openai.enabled:false}") boolean enabled
	) {
		this.restTemplate = restTemplate;
		this.apiKey = apiKey;
		this.model = model;
		this.enabled = enabled;
	}

	public String send(String input) {
		if (!enabled) {
			LOGGER.warn(API_URL + " is disabled");
			throw new IllegalStateException(API_URL + " is disabled");
		}

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("API Key is blank");
		}

		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Input is blank");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		Map<String, Object> body = Map.of(
				"model", model,
				"input", input,
				"reasoning", Map.of("effort", "none"),
				"max_output_tokens", MAX_OUTPUT_TOKENS,
				"store", false
		);

		JsonNode response;

		try {
			response = restTemplate.postForObject(API_URL, new HttpEntity<>(body, headers), JsonNode.class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("RetVal: " + e.getStatusCode().value() + e.getMessage());
		}

		if (response == null || !"completed".equals(response.path("status").asText())) {
			throw new IllegalStateException("Response was not completed");
		}

		for (JsonNode output : response.path("output")) {
			for (JsonNode content : output.path("content")) {
				if ("output_text".equals(content.path("type").asText())) {
					String text = content.path("text").asText();

					if (!text.isBlank()) {
						return text;
					}
				}
			}
		}

		throw new IllegalStateException("No text returned");

	}


}
