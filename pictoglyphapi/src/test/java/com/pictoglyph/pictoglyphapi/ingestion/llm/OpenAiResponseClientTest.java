
package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiResponseClientTest {

	private static final String API_URL = "https://api.openai.com/v1/responses";

	@Test
	void shouldSendRequestAndReadResponse() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("gpt-6-luna"))
				.andExpect(jsonPath("$.store").value(false))
				.andRespond(withSuccess(completedResponse("Hello from GPT"), MediaType.APPLICATION_JSON));

		assertThat(client.send("Say hello")).isEqualTo("Hello from GPT");

		server.verify();
	}

	@Test
	void shouldReturnStructuredMappingSuggestions() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.text.format.type").value("json_schema"))
				.andExpect(jsonPath("$.text.format.strict").value(true))
				.andExpect(jsonPath("$.text.format.schema.properties.suggestions.items.properties.target.enum[0]").value("SYMBOL_CODE"))
				.andExpect(jsonPath("$.max_output_tokens").value(1024))
				.andExpect(jsonPath("$.store").value(false))
				.andRespond(withSuccess(completedResponse("""
						{
							"suggestions": [
								{
									"target": "SYMBOL_CODE",
									"sourceField": "glyph_id",
									"reason": "Likely sign identifier"
								}
							]
						}
						"""), MediaType.APPLICATION_JSON));

		var result = client.sendStructuredMapping("Suggest a mapping");

		assertThat(result.path("suggestions").size()).isEqualTo(1);
		assertThat(result.path("suggestions").get(0).path("target").asText()).isEqualTo("SYMBOL_CODE");
		assertThat(result.path("suggestions").get(0).path("sourceField").asText()).isEqualTo("glyph_id");

		server.verify();
	}

	@Test
	void shouldRejectMalformedMappingJson() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andRespond(withSuccess(completedResponse("not valid JSON"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.sendStructuredMapping("Suggest a mapping"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("malformed mapping JSON");

		server.verify();
	}

	@Test
	void shouldRejectMissingSuggestionsArray() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andRespond(withSuccess(completedResponse("""
						{"unexpected": []}
						"""), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.sendStructuredMapping("Suggest a mapping"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("invalid mapping response");

		server.verify();
	}

	@Test
	void shouldRejectUnknownMappingTarget() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andRespond(withSuccess(completedResponse("""
						{
							"suggestions": [
								{
									"target": "UNKNOWN",
									"sourceField": "glyph_id",
									"reason": "Unrecognised mapping target"
								}
							]
						}
						"""), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.sendStructuredMapping("Suggest a mapping"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("invalid mapping suggestion");

		server.verify();
	}

	@Test
	void shouldRejectIncompleteResponse() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andRespond(withSuccess("""
						{
							"status": "incomplete",
							"incomplete_details": {"reason": "max_output_tokens"},
							"output": []
						}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.sendStructuredMapping("Suggest a mapping"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not completed");

		server.verify();
	}

	@Test
	void shouldNotExposeApiErrorBody() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo(API_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("PRIVATE_API_ERROR_DETAILS"));

		assertThatThrownBy(() -> client.sendStructuredMapping("Suggest a mapping"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("HTTP status 401")
				.hasMessageNotContaining("PRIVATE_API_ERROR_DETAILS");

		server.verify();
	}

	@Test
	void shouldNotCallOpenAiWhenDisabled() {
		OpenAiResponseClient client = new OpenAiResponseClient(new RestTemplate(), "test-key", "gpt-6-luna", false);

		assertThatThrownBy(() -> client.send("Hello"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("disabled");
	}

	private String completedResponse(String text) {
		ObjectNode response = new ObjectMapper().createObjectNode();
		response.put("status", "completed");

		ObjectNode output = response.putArray("output").addObject();
		output.put("type", "message");
		output.putArray("content").addObject().put("type", "output_text").put("text", text);

		return response.toString();
	}
}
