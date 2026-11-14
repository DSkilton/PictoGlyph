package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalResponse;
import com.pictoglyph.pictoglyphapi.ingestion.llm.GptFieldSuggestion;
import com.pictoglyph.pictoglyphapi.ingestion.llm.GptMappingAdvisor;
import com.pictoglyph.pictoglyphapi.ingestion.llm.GptMappingFallbackService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldMatcher;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingConfidenceCalculator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSampleReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.DeterministicMappingStrategy;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.GptAssistedMappingStrategy;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.SourceMappingPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SourceMappingGptFallbackIntegrationTest {

	private static final String API_URL = "http://localhost:9000/museum-sample.json";

	@Mock
	private GptMappingAdvisor advisor;

	private MockRestServiceServer mockServer;
	private SourceMappingProposalService service;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		mockServer = MockRestServiceServer.bindTo(restTemplate).build();

		GptMappingFallbackService fallbackService = new GptMappingFallbackService(advisor, new SourceFieldValueReader(), true);
		SourceMappingPipeline pipeline = new SourceMappingPipeline(List.of(
						new DeterministicMappingStrategy(new SourceFieldMatcher()),
						new GptAssistedMappingStrategy(fallbackService)
				)
		);

		service = new SourceMappingProposalService(
				new SourceSampleReader(restTemplate, new ObjectMapper()),
				new SourceFieldDiscoveryService(),
				new SourceMappingConfidenceCalculator(),
				pipeline
		);
	}


	@Test
	void shouldAddGptCandidatesWhenRequiredMappingsAreMissing() {
		mockServer.expect(requestTo(API_URL)).andRespond(withSuccess("""
				{
					"items": [
						{
							"glyph_ref": "A1",
							"archive_picture": "https://example.org/images/a1.jpg",
							"title": "First sign"
						},
						{
							"glyph_ref": "B2",
							"archive_picture": "https://example.org/images/b2.png",
							"title": "Second sign"
						}
					]
				}
				""", MediaType.APPLICATION_JSON));

		when(advisor.suggest(eq("Unfamiliar museum"), anySet())).thenReturn(List.of(
				new GptFieldSuggestion(SourceMappingTarget.SYMBOL_CODE, "glyph_ref", "Likely sign identifier"),
				new GptFieldSuggestion(SourceMappingTarget.IMAGE_PATH, "archive_picture", "Likely image reference")
		));

		SourceMappingProposalResponse result = service.proposeMapping(
				new SourceMappingProposalRequest("Unfamiliar museum", API_URL, null)
		);

		assertThat(result.proposedMapping().symbolCodeField()).isEqualTo("glyph_ref");
		assertThat(result.proposedMapping().imagePathField()).isEqualTo("archive_picture");
		assertThat(result.proposedMapping().titleField()).isEqualTo("title");

		assertThat(result.evidence()).anySatisfy(evidence -> {
			assertThat(evidence.targetField()).isEqualTo("symbolCodeField");
			assertThat(evidence.reason()).contains("GPT candidate");
			assertThat(evidence.confidence()).isEqualTo(0.50);
		});

		verify(advisor).suggest(eq("Unfamiliar museum"), anySet());
		mockServer.verify();
	}

	@Test
	void shouldKeepDeterministicMappingsWithoutCallingGpt() {
		mockServer.expect(requestTo(API_URL)).andRespond(withSuccess("""
				{
					"items": [
						{
							"symbolCode": "A1",
							"imageUrl": "https://example.org/a1.jpg"
						}
					]
				}
				""", MediaType.APPLICATION_JSON));

		SourceMappingProposalResponse result = service.proposeMapping(
				new SourceMappingProposalRequest("Known source", API_URL, null)
		);

		assertThat(result.proposedMapping().symbolCodeField()).isEqualTo("symbolCode");
		assertThat(result.proposedMapping().imagePathField()).isEqualTo("imageUrl");

		verifyNoInteractions(advisor);
		mockServer.verify();
	}

	@Test
	void shouldKeepJavaResultsWhenGptFails() {
		mockServer.expect(requestTo(API_URL)).andRespond(withSuccess("""
				{
					"items": [
						{
							"glyph_ref": "A1",
							"title": "First sign"
						}
					]
				}
				""", MediaType.APPLICATION_JSON));

		when(advisor.suggest(eq("Unavailable GPT"), anySet())).thenThrow(new IllegalStateException("API unavailable"));

		SourceMappingProposalResponse result = service.proposeMapping(new SourceMappingProposalRequest("Unavailable GPT", API_URL, null));

		assertThat(result.proposedMapping().symbolCodeField()).isNull();
		assertThat(result.proposedMapping().imagePathField()).isNull();
		assertThat(result.proposedMapping().titleField()).isEqualTo("title");

		verify(advisor).suggest(eq("Unavailable GPT"), anySet());
		mockServer.verify();
	}
}
