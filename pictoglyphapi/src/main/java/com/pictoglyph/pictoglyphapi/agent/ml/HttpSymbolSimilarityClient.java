package com.pictoglyph.pictoglyphapi.agent.ml;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class HttpSymbolSimilarityClient implements SymbolSimilarityClient {

	private static final String SYMBOLS_PATH = "/symbols/";
	public static final String SIMILAR_PATH = "/similar";

	private final RestTemplate restTemplate;

	@Value("${pictoglyph.ml.base-url}")
	private String mlBaseUrl;

	@Override
	public SymbolSimilarityResponse findSimilarSymbols(Long symbolId) {
		return restTemplate.postForObject(
				buildSimilarityUrl(symbolId),
				null,
				SymbolSimilarityResponse.class
		);
	}

	private String buildSimilarityUrl(Long symbolId) {
		return mlBaseUrl + SYMBOLS_PATH + symbolId + SIMILAR_PATH;
	}
}