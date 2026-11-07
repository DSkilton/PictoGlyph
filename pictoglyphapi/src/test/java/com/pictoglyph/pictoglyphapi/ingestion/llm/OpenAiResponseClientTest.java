
package com.pictoglyph.pictoglyphapi.ingestion.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiResponseClientTest {

	@Test
	void shouldSendRequestAndReadResponse() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, "test-key", "gpt-6-luna", true);

		server.expect(requestTo("https://api.openai.com/v1/responses"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("gpt-6-luna"))
				.andExpect(jsonPath("$.store").value(false))
				.andRespond(withSuccess("""
						{
						  "status": "completed",
						  "output": [
						    {
						      "type": "message",
						      "content": [
						        {
						          "type": "output_text",
						          "text": "Hello from GPT"
						        }
						      ]
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		String response = client.send("Say hello");

		assertThat(response).isEqualTo("Hello from GPT");

		server.verify();
	}

	@Test
	void shouldNotCallOpenAiWhenDisabled() {
		OpenAiResponseClient client = new OpenAiResponseClient(new RestTemplate(), "test-key", "gpt-6-luna",false);

		assertThatThrownBy(() -> client.send("Hello"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("disabled");
	}
}
