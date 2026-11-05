package com.pictoglyph.pictoglyphapi.ingestion.llm;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GptFieldSuggestionTest {

	@Test
	void storesAnUnapprovedSourceMappingSuggestion() {
		GptFieldSuggestion suggestion = new GptFieldSuggestion(
				SourceMappingTarget.PERIOD,
				" production_date ",
				" Matches the source's production date "
		);

		assertThat(suggestion.target()).isEqualTo(SourceMappingTarget.PERIOD);
		assertThat(suggestion.sourceField()).isEqualTo("production_date");
		assertThat(suggestion.reason()).isEqualTo("Matches the source's production date");
	}

	@Test
	void rejectsSuggestionsWithMissingFields() {
		assertThatThrownBy(() ->
				new GptFieldSuggestion(
						SourceMappingTarget.PLACE,
						" ",
						"Possible location"
				)
		)
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() ->
				new GptFieldSuggestion(
						SourceMappingTarget.PLACE,
						"findspot",
						""
				)
		)
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUnknownTargets() {
		assertThatThrownBy(() ->
				new GptFieldSuggestion(
							null,
							"findspot",
							"A place of discovery"
				)
		)
				.isInstanceOf(NullPointerException.class);
	}
}
