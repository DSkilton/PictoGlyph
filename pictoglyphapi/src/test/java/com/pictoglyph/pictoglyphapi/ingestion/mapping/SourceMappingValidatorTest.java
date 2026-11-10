
package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMappingValidatorTest {

	private ObjectMapper objectMapper;
	private SourceMappingValidator validator;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		validator = new SourceMappingValidator(new SourceFieldValueReader());
	}

	@Test
	void shouldAcceptValidNestedMapping() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"results",
				"sign_code",
				"media.image_url",
				"display.title",
				null,
				"findspot.place",
				"production.period",
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "object_number": "EA123",
				    "sign_code": "G17",
				    "media": {
				      "image_url": "https://example.org/ea123.jpg"
				    },
				    "display": {
				      "title": "Hieroglyphic sign"
				    },
				    "findspot": {
				      "place": "Thebes"
				    },
				    "production": {
				      "period": "New Kingdom"
				    }
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isTrue();
		assertThat(result.errors()).isEmpty();
	}

	@Test
	void shouldRejectMissingRequiredMappings() {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				null,
				null,
				"title",
				null,
				null,
				null,
				null,
				null
		);

		SourceMappingValidationResult result = validator.validate(mapping, List.of());

		assertThat(result.valid()).isFalse();

		assertThat(result.errors())
				.contains(
						"symbolCodeField must be mapped",
						"imagePathField must be mapped"
				);
	}

	@Test
	void shouldRejectRequiredPathThatDoesNotExist() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"missing.signCode",
				"media.image_url",
				null,
				null,
				null,
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "media": {
				      "image_url": "https://example.org/a1.png"
				    }
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isFalse();

		assertThat(result.errors())
				.anySatisfy(error ->
						assertThat(error)
								.contains("symbolCodeField")
								.contains("missing.signCode")
				);
	}

	@Test
	void shouldWarnWhenOptionalMappingHasNoValues() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"symbolCode",
				"imageUrl",
				"title",
				null,
				"missing.place",
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "symbolCode": "A1",
				    "imageUrl": "https://example.org/a1.png",
				    "title": "Seated man"
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isTrue();
		assertThat(result.errors()).isEmpty();

		assertThat(result.warnings())
				.anySatisfy(warning ->
						assertThat(warning)
								.contains("placeField")
								.contains("missing.place")
				);
	}

	@Test
	void shouldRejectSameFieldForCodeAndImage() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"signCode",
				"signCode",
				null,
				null,
				null,
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "signCode": "A1"
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors())
				.contains(
						"symbolCodeField and imagePathField cannot use the same source field"
				);
	}

	@Test
	void shouldReportActualPercentageWhenOptionalFieldHasLowCoverage() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"symbolCode",
				"imageUrl",
				null,
				null,
				"place",
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "symbolCode": "A1",
				    "imageUrl": "https://example.org/a1.png",
				    "place": "Thebes"
				  },
				  {
				    "symbolCode": "A2",
				    "imageUrl": "https://example.org/a2.png"
				  },
				  {
				    "symbolCode": "A3",
				    "imageUrl": "https://example.org/a3.png"
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isTrue();
		assertThat(result.errors()).isEmpty();

		assertThat(result.warnings())
				.containsExactly(
						"placeField maps to place, but only 33% "
								+ "of sampled records contain a usable value"
				);
	}

	@Test
	void shouldRejectArtefactNumberAsSymbolCode() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"object_number",
				"imageUrl",
				null,
				null,
				null,
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "object_number": "EA123",
				    "imageUrl": "https://example.org/ea123.jpg"
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors())
				.anySatisfy(error ->
						assertThat(error)
								.contains("symbolCodeField")
								.contains("object_number")
								.contains("ARTEFACT_OR_RECORD")
				);
	}

	@Test
	void shouldRequireExplicitSemanticsForGenericId() throws Exception {
		SourceFieldMapping mapping = new SourceFieldMapping(
				"items",
				"id",
				"imageUrl",
				null,
				null,
				null,
				null,
				null,
				null
		);

		List<JsonNode> items = itemsFrom("""
				[
				  {
				    "id": "A1",
				    "imageUrl": "https://example.org/a1.jpg"
				  }
				]
				""");

		SourceMappingValidationResult result = validator.validate(mapping, items);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors())
				.anySatisfy(error ->
						assertThat(error)
								.contains("symbolCodeField")
								.contains("id")
								.contains("UNRESOLVED")
				);
	}

	private List<JsonNode> itemsFrom(String json) throws Exception {
		JsonNode arrayNode = objectMapper.readTree(json);

		return StreamSupport
				.stream(arrayNode.spliterator(), false)
				.toList();
	}
}
