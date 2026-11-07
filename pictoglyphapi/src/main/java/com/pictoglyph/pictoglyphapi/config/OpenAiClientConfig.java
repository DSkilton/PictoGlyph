
package com.pictoglyph.pictoglyphapi.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class OpenAiClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(45);

	@Bean("openAiRestTemplate")
	public RestTemplate openAiRestTemplate(RestTemplateBuilder builder) {
		return builder
				.setConnectTimeout(CONNECT_TIMEOUT)
				.setReadTimeout(READ_TIMEOUT)
				.build();
	}
}
