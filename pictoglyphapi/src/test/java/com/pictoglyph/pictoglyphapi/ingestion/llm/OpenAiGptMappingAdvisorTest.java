
package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiGptMappingAdvisorTest {

	@Mock
	private OpenAiResponseClient responseClient;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private OpenAiGptMappingAdvisor advisor;

	@BeforeEach
	void setUp() {
		advisor = new OpenAiGptMappingAdvisor(responseClient, objectMapper);
	}

	@Test
	void shouldAcceptValidSourceFieldMappings() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{
					"suggestions": [
						{
							"target": "SYMBOL_CODE",
							"sourceField": "glyph_id",
							"reason": "Likely sign identifier"
						},
						{
							"target": "IMAGE_PATH",
							"sourceField": "image_url",
							"reason": "Likely image reference"
						}
					]
				}
				"""));

		List<GptFieldSuggestion> results = advisor.suggest("Maya collection", Set.of("glyph_id", "image_url"));

		assertThat(results).hasSize(2);
		assertThat(results.get(0).target()).isEqualTo(SourceMappingTarget.SYMBOL_CODE);
		assertThat(results.get(0).sourceField()).isEqualTo("glyph_id");
		assertThat(results.get(1).target()).isEqualTo(SourceMappingTarget.IMAGE_PATH);
		assertThat(results.get(1).sourceField()).isEqualTo("image_url");

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(responseClient).sendStructuredMapping(prompt.capture());

		assertThat(prompt.getValue()).contains("\"glyph_id\"");
		assertThat(prompt.getValue()).contains("\"image_url\"");
	}

	@Test
	void shouldKeepValidSuggestionsWhenAnotherFieldIsInvented() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{
					"suggestions": [
						{
							"target": "SYMBOL_CODE",
							"sourceField": "invented_code",
							"reason": "Possible identifier"
						},
						{
							"target": "IMAGE_PATH",
							"sourceField": "image_url",
							"reason": "Image reference"
						}
					]
				}
				"""));

		List<GptFieldSuggestion> results = advisor.suggest("Maya collection", Set.of("glyph_id", "image_url"));

		assertThat(results).hasSize(1);
		assertThat(results.get(0).target()).isEqualTo(SourceMappingTarget.IMAGE_PATH);
		assertThat(results.get(0).sourceField()).isEqualTo("image_url");
	}

	@Test
	void shouldRejectConflictingTargetsWithoutLosingOtherMappings() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{
					"suggestions": [
						{
							"target": "PLACE",
							"sourceField": "findspot",
							"reason": "Find location"
						},
						{
							"target": "PLACE",
							"sourceField": "museum_location",
							"reason": "Museum location"
						},
						{
							"target": "IMAGE_PATH",
							"sourceField": "image_url",
							"reason": "Image reference"
						}
					]
				}
				"""));

		List<GptFieldSuggestion> results = advisor.suggest("Museum", Set.of("findspot", "museum_location", "image_url"));

		assertThat(results).hasSize(1);
		assertThat(results.get(0).target()).isEqualTo(SourceMappingTarget.IMAGE_PATH);
	}

	@Test
	void shouldRejectReusedSourceFields() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{
					"suggestions": [
						{
							"target": "TITLE",
							"sourceField": "name",
							"reason": "Display name"
						},
						{
							"target": "PLACE",
							"sourceField": "name",
							"reason": "Possible location"
						}
					]
				}
				"""));

		assertThat(advisor.suggest("Museum", Set.of("name"))).isEmpty();
	}

	@Test
	void shouldAcceptEmptySuggestionList() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{"suggestions": []}
				"""));

		assertThat(advisor.suggest("Museum", Set.of("unknown_field"))).isEmpty();
	}

	@Test
	void shouldRejectInvalidInputBeforeCallingApi() {
		assertThatThrownBy(() -> advisor.suggest("", Set.of("glyph_id"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> advisor.suggest("Museum", Set.of())).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(responseClient);
	}

	@Test
	void shouldRejectInvalidResponseStructure() throws Exception {
		when(responseClient.sendStructuredMapping(anyString())).thenReturn(json("""
				{"unexpected": []}
				"""));

		assertThatThrownBy(() -> advisor.suggest("Museum", Set.of("glyph_id")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Invalid GPT mapping response");
	}

	private JsonNode json(String value) throws Exception {
		return objectMapper.readTree(value);
	}
}
