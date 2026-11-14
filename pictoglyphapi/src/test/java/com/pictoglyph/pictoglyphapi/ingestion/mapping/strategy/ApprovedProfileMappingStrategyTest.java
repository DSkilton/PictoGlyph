
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSchemaDriftDetector;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.ApiSourceProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovedProfileMappingStrategyTest {

	private static final String SOURCE = "Test Museum";
	private static final String API_URL =
			"https://example.org/api/symbols";

	@Mock
	private ApiSourceProfileRepository repository;
	private final ObjectMapper mapper = new ObjectMapper();
	private ApprovedProfileMappingStrategy strategy;

	@BeforeEach
	void setUp() {
		SourceFieldValueReader valueReader = new SourceFieldValueReader();

		strategy = new ApprovedProfileMappingStrategy(repository, new SourceSchemaDriftDetector(), new SourceMappingValidator(valueReader), valueReader);
	}

	@Test
	void shouldReuseApprovedMappingAndStopPipeline() throws Exception {
		givenApprovedProfile();
		SourceMappingContext context = context("""
                [
                  {
                    "glyph_ref": "A1",
                    "archive_picture": "https://example.org/a1.jpg",
                    "title": "Sign A1"
                  },
                  {
                    "glyph_ref": "B2",
                    "archive_picture": "https://example.org/b2.jpg",
                    "title": "Sign B2"
                  }
                ]
                """, Set.of(
				"glyph_ref",
				"archive_picture",
				"title"
		));

		MappingStep result = strategy.apply(context, Map.of());

		assertThat(result.stop()).isTrue();

		assertThat(result.matches()
				.get(SourceMappingTarget.SYMBOL_CODE)
				.sourceField())
				.isEqualTo("glyph_ref");

		assertThat(result.matches()
				.get(SourceMappingTarget.IMAGE_PATH)
				.sourceField())
				.isEqualTo("archive_picture");

		assertThat(result.matches()
				.get(SourceMappingTarget.SYMBOL_CODE)
				.reason())
				.contains("approved source profile #7");
	}

	@Test
	void shouldNotReuseMappingWhenSchemaChanges() throws Exception {
		givenApprovedProfile();

		SourceMappingContext context = context("""
                [
                  {
                    "glyph_ref": "A1",
                    "archive_picture": "https://example.org/a1.jpg"
                  }
                ]
                """, Set.of(
				"glyph_ref",
				"archive_picture"
		));

		MappingStep result = strategy.apply(context, Map.of());

		assertThat(result.stop()).isFalse();
		assertThat(result.matches()).isEmpty();
	}

	@Test
	void shouldRejectIncompleteRequiredSampleValues() throws Exception {
		givenApprovedProfile();

		SourceMappingContext context = context("""
                [
                  {
                    "glyph_ref": "A1",
                    "archive_picture": "https://example.org/a1.jpg",
                    "title": "Sign A1"
                  },
                  {
                    "glyph_ref": "B2",
                    "archive_picture": "",
                    "title": "Sign B2"
                  }
                ]
                """, Set.of(
				"glyph_ref",
				"archive_picture",
				"title"
		));

		MappingStep result = strategy.apply(context, Map.of());

		assertThat(result.stop()).isFalse();
		assertThat(result.matches()).isEmpty();
	}

	@Test
	void shouldNotCallSubsequentStrategiesWhenReused() throws Exception {
		givenApprovedProfile();

		SourceMappingStrategy nextStrategy = mock(SourceMappingStrategy.class);
		SourceMappingPipeline pipeline = new SourceMappingPipeline(List.of(strategy, nextStrategy));

		SourceMappingContext context = context("""
                [
                  {
                    "glyph_ref": "A1",
                    "archive_picture": "https://example.org/a1.jpg",
                    "title": "Sign A1"
                  }
                ]
                """, Set.of(
				"glyph_ref",
				"archive_picture",
				"title"
		));

		var result = pipeline.resolve(context);

		assertThat(result).containsKeys(SourceMappingTarget.SYMBOL_CODE, SourceMappingTarget.IMAGE_PATH);

		verifyNoInteractions(nextStrategy);
	}

	@Test
	void shouldContinueWhenNoApprovedProfileExists() throws Exception {
		when(repository.findFirstBySourceNameIgnoreCaseAndApiUrlAndStatusOrderByApprovedAtDescIdDesc(SOURCE, API_URL, ApiSourceProfileStatus.APPROVED)).thenReturn(Optional.empty());

		SourceMappingContext context = context("""
                [
                  {"glyph_ref": "A1"}
                ]
                """, Set.of("glyph_ref"));

		MappingStep result = strategy.apply(context, Map.of());

		assertThat(result.stop()).isFalse();
		assertThat(result.matches()).isEmpty();
	}

	private void givenApprovedProfile() {
		when(repository.findFirstBySourceNameIgnoreCaseAndApiUrlAndStatusOrderByApprovedAtDescIdDesc(SOURCE, API_URL, ApiSourceProfileStatus.APPROVED)).thenReturn(Optional.of(approvedProfile()));
	}

	private ApiSourceProfile approvedProfile() {

		return ApiSourceProfile.builder()
				.id(7L)
				.profileName("Test museum symbols")
				.sourceName(SOURCE)
				.apiUrl(API_URL)
				.status(ApiSourceProfileStatus.APPROVED)
				.approvedAt(LocalDateTime.now())
				.itemArrayField("items")
				.symbolCodeField("glyph_ref")
				.imagePathField("archive_picture")
				.titleField("title")
				.approvedSchemaFields(
						mapper.valueToTree(
								List.of(
										"glyph_ref",
										"archive_picture",
										"title"
								)
						)
				)
				.build();
	}

	private SourceMappingContext context(String json, Set<String> fields) throws Exception {
		JsonNode array = mapper.readTree(json);

		List<JsonNode> items = StreamSupport.stream(array.spliterator(), false).toList();

		return new SourceMappingContext(SOURCE, API_URL, new SourceSample("items", items), fields);
	}
}
