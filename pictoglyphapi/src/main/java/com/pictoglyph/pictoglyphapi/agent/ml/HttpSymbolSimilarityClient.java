package com.pictoglyph.pictoglyphapi.agent.ml;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class HttpSymbolSimilarityClient implements SymbolSimilarityClient {

	private final RestTemplate restTemplate;

	@Value("${pictoglyph.ml.base-url")
	private String mlBaseUrl;

	@Override
	public SymbolSimilarityResponse findSimilarSymbols(Long symbolId) {
		String url = mlBaseUrl + "/symbols/" + symbolId + "/similar";

		return restTemplate.postForObject(
				url,
				null,
				SymbolSimilarityResponse.class
		);
	}
}
