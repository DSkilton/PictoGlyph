
package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.pictoglyph.pictoglyphapi.config.OpenAiClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponseClientLiveTest {

	@Test
	@EnabledIfEnvironmentVariable(
			named = "PICTOGLYPH_OPENAI_LIVE_TEST",
			matches = "true"
	)
	void shouldConnectToOpenAi() {
		String apiKey = System.getenv("OPENAI_API_KEY");

		assertThat(apiKey).isNotBlank();

		RestTemplate restTemplate = new OpenAiClientConfig().openAiRestTemplate(new RestTemplateBuilder());

		OpenAiResponseClient client = new OpenAiResponseClient(restTemplate, apiKey, "gpt-6-luna",true);

		String response = client.send("Reply with the single word CONNECTED.");

		assertThat(response).isNotBlank();
	}
}
