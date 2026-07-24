package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFieldMatcherTest {

	private ObjectMapper objectMapper;
	private SourceFieldMatcher matcher;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		matcher = new SourceFieldMatcher();
	}

	@Test
	void shouldNotAwardImageValueBonusToGenericWebPageUrl()
			throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "url": "https://example.org/object/123"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(Set.of("url"), List.of(item));
		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isEqualTo("url");
		assertThat(imageMatch.confidence()).isEqualTo(0.85);
		assertThat(imageMatch.reason()).isEqualTo("Exact field-name match");
	}

	@Test
	void shouldAwardImageValueBonusToUrlWithImageExtension() throws Exception {
		JsonNode item = objectMapper.readTree("""
			{
			  "url": "https://example.org/images/a1.png"
			}
			""");

		Map<SourceMappingTarget, FieldMatch> matches =
				matcher.matchFields(
						Set.of("url"),
						List.of(item)
				);

		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isEqualTo("url");
		assertThat(imageMatch.confidence()).isEqualTo(1.0);

		assertThat(imageMatch.reason()).isEqualTo("Exact field-name match; Sample values match expected pattern"
				);
	}
}