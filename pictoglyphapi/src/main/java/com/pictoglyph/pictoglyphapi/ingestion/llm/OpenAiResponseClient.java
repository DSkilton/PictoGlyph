
package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiResponseClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiResponseClient.class);
	private static final String API_URL = "https://api.openai.com/v1/responses";
	private static final int MAX_OUTPUT_TOKENS = 128;
	private static final int STRUCTURED_MAX_OUTPUT_TOKENS = 1024;

	private static final List<String> TARGET_NAMES = Arrays.stream(SourceMappingTarget.values()).map(Enum::name).toList();

	private static final Map<String, Object> MAPPING_SCHEMA = Map.of(
			"type", "object",
			"properties", Map.of(
					"suggestions", Map.of(
							"type", "array",
							"items", Map.of(
									"type", "object",
									"properties", Map.of(
											"target", Map.of("type", "string", "enum", TARGET_NAMES),
											"sourceField", Map.of("type", "string"),
											"reason", Map.of("type", "string")
									),
									"required", List.of("target", "sourceField", "reason"),
									"additionalProperties", false
							)
					)
			),
			"required", List.of("suggestions"),
			"additionalProperties", false
	);

	private final RestTemplate restTemplate;
	private final String apiKey;
	private final String model;
	private final boolean enabled;
	private final ObjectMapper objectMapper = new ObjectMapper();

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
		return sendRequest(input, false);
	}

	public JsonNode sendStructuredMapping(String input) {
		String output = sendRequest(input, true);

		JsonNode mapping;

		try {
			mapping = objectMapper.readTree(output);
		} catch (JsonProcessingException e) {
			LOGGER.warn("OpenAI returned malformed structured mapping JSON");
			throw new IllegalStateException("OpenAI returned malformed mapping JSON");
		}

		if (mapping == null || !mapping.isObject() || !mapping.path("suggestions").isArray()) {
			throw new IllegalStateException("OpenAI returned an invalid mapping response");
		}

		for (JsonNode suggestion : mapping.path("suggestions")) {
			if (!suggestion.isObject()
					|| !TARGET_NAMES.contains(suggestion.path("target").asText())
					|| !suggestion.path("sourceField").isTextual()
					|| suggestion.path("sourceField").asText().isBlank()
					|| !suggestion.path("reason").isTextual()
					|| suggestion.path("reason").asText().isBlank()) {
				throw new IllegalStateException("OpenAI returned an invalid mapping suggestion");
			}
		}

		LOGGER.info("Received structured mapping suggestions: count={}", mapping.path("suggestions").size());

		return mapping;
	}

	private String sendRequest(String input, boolean structured) {
		if (!enabled) {
			LOGGER.warn("OpenAI Responses API is disabled");
			throw new IllegalStateException("OpenAI Responses API is disabled");
		}

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OpenAI API key is blank");
		}

		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Input is blank");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		Map<String, Object> body = new HashMap<>();
		body.put("model", model);
		body.put("input", input);
		body.put("reasoning", Map.of("effort", "none"));
		body.put("max_output_tokens", structured ? STRUCTURED_MAX_OUTPUT_TOKENS : MAX_OUTPUT_TOKENS);
		body.put("store", false);

		if (structured) {
			body.put("text", Map.of("format", Map.of(
					"type", "json_schema",
					"name", "pictoglyph_mapping_suggestions",
					"strict", true,
					"schema", MAPPING_SCHEMA
			)));
		}

		JsonNode response;

		try {
			response = restTemplate.postForObject(API_URL, new HttpEntity<>(body, headers), JsonNode.class);
		} catch (RestClientResponseException e) {
			LOGGER.warn("OpenAI Responses API returned HTTP {}", e.getStatusCode().value());
			throw new IllegalStateException("OpenAI request failed with HTTP status " + e.getStatusCode().value());
		} catch (RestClientException e) {
			LOGGER.warn("OpenAI request failed: errorType={}", e.getClass().getSimpleName());
			throw new IllegalStateException("OpenAI request failed");
		}

		if (response == null || !"completed".equals(response.path("status").asText())) {
			LOGGER.warn("OpenAI response was not completed");
			throw new IllegalStateException("OpenAI response was not completed");
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

		throw new IllegalStateException("OpenAI returned no output text");
	}
}
