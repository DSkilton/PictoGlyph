package com.pictoglyph.pictoglyphapi.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class OpenAiClientConfig {
	private static final int FIVE = 5;
	private static final int FORTY_FIVE = 45;

	@Bean
	public RestTemplate openAiRestTemplate(RestTemplateBuilder builder) {
		return builder
				.setConnectTimeout(Duration.ofSeconds(FIVE))
				.setReadTimeout(Duration.ofSeconds(FORTY_FIVE))
				.build();
	}
}
