
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
	void shouldNotMapGenericWebPageUrlAsImage() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "url": "https://example.org/object/123"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("url"),
				List.of(item)
		);

		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isNull();
		assertThat(imageMatch.confidence()).isZero();
		assertThat(imageMatch.reason()).isEqualTo("No suitable source field found");
	}

	@Test
	void shouldAwardImageValueBonusToUrlWithImageExtension() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "url": "https://example.org/images/a1.png"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("url"),
				List.of(item)
		);

		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isEqualTo("url");
		assertThat(imageMatch.confidence()).isEqualTo(1.0);
		assertThat(imageMatch.reason())
				.isEqualTo("Exact field-name match; Sample values match expected pattern");
	}

	@Test
	void shouldNotMapGenericContentUrlWhenItPointsToWebPage() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "contentUrl": "https://example.org/collection/123"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("contentUrl"),
				List.of(item)
		);

		assertThat(matches.get(SourceMappingTarget.IMAGE_PATH).sourceField()).isNull();
	}

	@Test
	void shouldAcceptExplicitImageUrlWithoutFileExtension() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "imageUrl": "https://example.org/image-service/123"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("imageUrl"),
				List.of(item)
		);

		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isEqualTo("imageUrl");
		assertThat(imageMatch.confidence()).isEqualTo(0.85);
	}

	@Test
	void shouldRejectUnsupportedImageUrlScheme() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "url": "ftp://example.org/images/a1.png"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("url"),
				List.of(item)
		);

		assertThat(matches.get(SourceMappingTarget.IMAGE_PATH).sourceField()).isNull();
	}

	@Test
	void shouldRecogniseNestedImageUrl() throws Exception {
		JsonNode item = objectMapper.readTree("""
				{
				  "media": {
				    "url": "https://example.org/images/g17.jpg"
				  }
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("media.url"),
				List.of(item)
		);

		FieldMatch imageMatch = matches.get(SourceMappingTarget.IMAGE_PATH);

		assertThat(imageMatch.sourceField()).isEqualTo("media.url");
		assertThat(imageMatch.confidence()).isEqualTo(1.0);
	}

	@Test
	void shouldRejectGenericUrlWithInsufficientImageEvidence() throws Exception {
		JsonNode first = objectMapper.readTree("""
				{
				  "url": "https://example.org/images/a1.jpg"
				}
				""");

		JsonNode second = objectMapper.readTree("""
				{
				  "url": "https://example.org/object/123"
				}
				""");

		JsonNode third = objectMapper.readTree("""
				{
				  "url": "https://example.org/object/456"
				}
				""");

		Map<SourceMappingTarget, FieldMatch> matches = matcher.matchFields(
				Set.of("url"),
				List.of(first, second, third)
		);

		assertThat(matches.get(SourceMappingTarget.IMAGE_PATH).sourceField())
				.isNull();
	}
}
