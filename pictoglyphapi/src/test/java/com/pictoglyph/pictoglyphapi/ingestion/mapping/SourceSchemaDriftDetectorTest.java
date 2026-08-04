package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SourceSchemaDriftDetectorTest {

	private SourceSchemaDriftDetector detector;

	@BeforeEach
	void setUp() {
		detector = new SourceSchemaDriftDetector();
	}

	@Test
	void shouldReportMatchingSchemas() {
		Set<String> approvedFields = Set.of(
				"symbolCode",
				"imageUrl",
				"label"
		);

		Set<String> currentFields = Set.of(
				"symbolCode",
				"imageUrl",
				"label"
		);

		SourceSchemaComparison result = detector.comparison(approvedFields, currentFields);

		assertThat(result.matches()).isTrue();
		assertThat(result.hasBreakingChanges()).isFalse();
		assertThat(result.addedFields()).isEmpty();
		assertThat(result.removedFields()).isEmpty();
	}

	@Test
	void shouldReportNewFieldsAsAdded() {
		Set<String> approvedFields = Set.of(
				"symbolCode",
				"imageUrl"
		);

		Set<String> currentFields = Set.of(
				"symbolCode",
				"imageUrl",
				"description"
		);

		SourceSchemaComparison result = detector.comparison(approvedFields, currentFields);

		assertThat(result.matches()).isFalse();
		assertThat(result.hasBreakingChanges()).isFalse();
		assertThat(result.addedFields()).containsExactly("description");
		assertThat(result.removedFields()).isEmpty();
	}

	@Test
	void shouldReportRemovedFieldsAsBreakingChanges() {
		Set<String> approvedFields = Set.of(
				"symbolCode",
				"media.imageUrl",
				"label"
		);

		Set<String> currentFields = Set.of(
				"symbolCode",
				"label"
		);

		SourceSchemaComparison result = detector.comparison(approvedFields, currentFields);

		assertThat(result.matches()).isFalse();
		assertThat(result.hasBreakingChanges()).isTrue();
		assertThat(result.addedFields()).isEmpty();
		assertThat(result.removedFields()).containsExactly("media.imageUrl");
	}

	@Test
	void shouldIgnoreNullAndBlankFieldNames() {
		Set<String> approvedFields = new java.util.HashSet<>();
		approvedFields.add("symbolCode");
		approvedFields.add(" ");
		approvedFields.add(null);

		Set<String> currentFields = Set.of("symbolCode");

		SourceSchemaComparison result = detector.comparison(approvedFields, currentFields);
		assertThat(result.matches()).isTrue();
	}

	@Test
	void shouldHandleNullFieldSets() {
		SourceSchemaComparison result = detector.comparison(null, null);

		assertThat(result.matches()).isTrue();
		assertThat(result.addedFields()).isEmpty();
		assertThat(result.removedFields()).isEmpty();
	}
}