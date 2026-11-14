package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GptMappingFallbackServiceTest {

	@Mock
	private GptMappingAdvisor advisor;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SourceFieldValueReader valueReader = new SourceFieldValueReader();
	private GptMappingFallbackService fallback;

	@BeforeEach
	void setUp() {
		fallback = new GptMappingFallbackService(advisor, valueReader, true);
	}

	@Test
	void shouldNotCallGptWhenDisabled() throws Exception {
		GptMappingFallbackService disabled = new GptMappingFallbackService(advisor, valueReader, false);
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		Map<SourceMappingTarget, FieldMatch> result = disabled.enrich("Museum", Set.of("glyph_ref"), samples("""
				[{"glyph_ref":"A1"}]
				"""), original);

		assertThat(result).isSameAs(original);
		verifyNoInteractions(advisor);
	}

	@Test
	void shouldNotCallGptWhenRequiredFieldsAreAlreadyMapped() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = completeMatches();
		Map<SourceMappingTarget, FieldMatch> result = fallback.enrich("Museum", Set.of("symbolCode", "imageUrl"), samples("""
				[{"symbolCode":"A1","imageUrl":"https://example.org/a1.jpg"}]
				"""), original);

		assertThat(result).isSameAs(original);
		verifyNoInteractions(advisor);
	}

	@Test
	void shouldFillMissingRequiredMappingWithoutOverwritingJavaResults() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		original.put(SourceMappingTarget.IMAGE_PATH, new FieldMatch(SourceMappingTarget.IMAGE_PATH, "imageUrl", 0.95, "Exact field-name match"));
		Set<String> fields = Set.of("glyph_ref", "imageUrl");
		when(advisor.suggest(eq("Museum"), eq(fields))).thenReturn(List.of(
				new GptFieldSuggestion(SourceMappingTarget.SYMBOL_CODE, "glyph_ref", "Likely sign code"),
				new GptFieldSuggestion(SourceMappingTarget.IMAGE_PATH, "glyph_ref", "Incorrect second mapping")
		));

		Map<SourceMappingTarget, FieldMatch> result = fallback.enrich("Museum", fields, samples("""
				[{"glyph_ref":"A1","imageUrl":"https://example.org/a1.jpg"},{"glyph_ref":"B2","imageUrl":"https://example.org/b2.png"}]
				"""), original);

		assertThat(result.get(SourceMappingTarget.SYMBOL_CODE).sourceField()).isEqualTo("glyph_ref");
		assertThat(result.get(SourceMappingTarget.SYMBOL_CODE).confidence()).isEqualTo(0.50);
		assertThat(result.get(SourceMappingTarget.SYMBOL_CODE).reason()).contains("GPT candidate").contains("2/2");
		assertThat(result.get(SourceMappingTarget.IMAGE_PATH)).isEqualTo(original.get(SourceMappingTarget.IMAGE_PATH));
		assertThat(original).doesNotContainKey(SourceMappingTarget.SYMBOL_CODE);
		verify(advisor).suggest("Museum", fields);
	}

	@Test
	void shouldRejectInventedAndUnsupportedImageFields() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		Set<String> fields = Set.of("glyph_ref", "landing_page");
		when(advisor.suggest(eq("Museum"), eq(fields))).thenReturn(List.of(
				new GptFieldSuggestion(SourceMappingTarget.IMAGE_PATH, "landing_page", "Possible image"),
				new GptFieldSuggestion(SourceMappingTarget.PLACE, "invented_place", "Likely location")
		));

		Map<SourceMappingTarget, FieldMatch> result = fallback.enrich("Museum", fields, samples("""
				[{"glyph_ref":"A1","landing_page":"https://example.org/collection/123"}]
				"""), original);

		assertThat(result).isEmpty();
	}

	@Test
	void shouldRejectFieldsWithInsufficientCoverage() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		Set<String> fields = Set.of("glyph_ref");
		when(advisor.suggest(eq("Museum"), eq(fields))).thenReturn(List.of(new GptFieldSuggestion(SourceMappingTarget.SYMBOL_CODE, "glyph_ref", "Possibly a code")));

		Map<SourceMappingTarget, FieldMatch> result = fallback.enrich("Museum", fields, samples("""
				[{"glyph_ref":"A1"},{},{"glyph_ref":null}]
				"""), original);

		assertThat(result).isEmpty();
	}

	@Test
	void shouldKeepDeterministicMappingWhenGptFails() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		original.put(SourceMappingTarget.IMAGE_PATH, new FieldMatch(SourceMappingTarget.IMAGE_PATH, "imageUrl", 0.95, "Matched"));
		Set<String> fields = Set.of("glyph_ref", "imageUrl");
		when(advisor.suggest(eq("Museum"), eq(fields))).thenThrow(new IllegalStateException("API not available"));

		Map<SourceMappingTarget, FieldMatch> result = fallback.enrich("Museum", fields, samples("""
				[{"glyph_ref":"A1","imageUrl":"a1.jpg"}]
				"""), original);

		assertThat(result).isSameAs(original);
		assertThat(result.get(SourceMappingTarget.IMAGE_PATH).sourceField()).isEqualTo("imageUrl");
	}


	@Test
	void shouldRejectGptSuggestionOfArtefactIdentifier() throws Exception {
		Map<SourceMappingTarget, FieldMatch> original = new EnumMap<>(SourceMappingTarget.class);
		Set<String> fields = Set.of("object_number", "imageUrl");

		when(advisor.suggest(eq("Museum"), eq(fields)))
				.thenReturn(List.of(
						new GptFieldSuggestion(
								SourceMappingTarget.SYMBOL_CODE,
								"object_number",
								"Possible identifier"
						)
				));

		Map<SourceMappingTarget, FieldMatch> result =
				fallback.enrich(
						"Museum",
						fields,
						samples("""
                            [
                              {
                                "object_number": "EA123",
                                "imageUrl": "https://example.org/a.jpg"
                              }
                            ]
                            """),
						original
				);

		assertThat(result).doesNotContainKey(SourceMappingTarget.SYMBOL_CODE);
		verify(advisor).suggest("Museum", fields);
	}

	private Map<SourceMappingTarget, FieldMatch> completeMatches() {
		Map<SourceMappingTarget, FieldMatch> result = new EnumMap<>(SourceMappingTarget.class);
		result.put(SourceMappingTarget.SYMBOL_CODE, new FieldMatch(SourceMappingTarget.SYMBOL_CODE, "symbolCode", 0.95, "Matched"));
		result.put(SourceMappingTarget.IMAGE_PATH, new FieldMatch(SourceMappingTarget.IMAGE_PATH, "imageUrl", 0.95, "Matched"));
		return result;
	}

	private List<JsonNode> samples(String json) throws Exception {
		JsonNode array = objectMapper.readTree(json);
		return java.util.stream.StreamSupport.stream(array.spliterator(), false).toList();
	}
}
