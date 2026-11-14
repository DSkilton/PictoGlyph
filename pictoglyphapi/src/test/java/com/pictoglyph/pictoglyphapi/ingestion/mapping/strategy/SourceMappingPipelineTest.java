
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SourceMappingPipelineTest {

	@Test
	void shouldStopWhenStrategyResolvesRequiredMappings() {
		SourceMappingStrategy first = mock(SourceMappingStrategy.class);
		SourceMappingStrategy second = mock(SourceMappingStrategy.class);

		SourceMappingContext context = context();

		Map<SourceMappingTarget, FieldMatch> matches =
				Map.of(
						SourceMappingTarget.SYMBOL_CODE,
						new FieldMatch(SourceMappingTarget.SYMBOL_CODE, "symbolCode", 0.95, "Matched"),

						SourceMappingTarget.IMAGE_PATH,
						new FieldMatch(SourceMappingTarget.IMAGE_PATH, "imageUrl", 0.95, "Matched")
				);

		when(first.id()).thenReturn("FIRST");

		when(first.apply(eq(context), anyMap())).thenReturn(MappingStep.stopWith(matches));

		SourceMappingPipeline pipeline = new SourceMappingPipeline(List.of(first, second));

		var result = pipeline.resolve(context);

		assertThat(result).containsAllEntriesOf(matches);

		verifyNoInteractions(second);
	}

	@Test
	void shouldContinueWhenFirstStrategyCannotResolveMapping() {
		SourceMappingStrategy first = mock(SourceMappingStrategy.class);
		SourceMappingStrategy second = mock(SourceMappingStrategy.class);

		SourceMappingContext context = context();

		Map<SourceMappingTarget, FieldMatch> partial =
				Map.of(
						SourceMappingTarget.SYMBOL_CODE,
						new FieldMatch(SourceMappingTarget.SYMBOL_CODE, "glyph_ref", 0.80, "Deterministic candidate")
				);

		Map<SourceMappingTarget, FieldMatch> completed =
				Map.of(
						SourceMappingTarget.SYMBOL_CODE,
						partial.get(SourceMappingTarget.SYMBOL_CODE),

						SourceMappingTarget.IMAGE_PATH,
						new FieldMatch(SourceMappingTarget.IMAGE_PATH, "archive_picture", 0.50, "GPT candidate; requires validation")
				);

		when(first.id()).thenReturn("FIRST");
		when(second.id()).thenReturn("SECOND");

		when(first.apply(eq(context), anyMap())).thenReturn(MappingStep.continueWith(partial));
		when(second.apply(eq(context), anyMap())).thenReturn(MappingStep.continueWith(completed));

		SourceMappingPipeline pipeline = new SourceMappingPipeline(List.of(first, second));

		var result = pipeline.resolve(context);

		assertThat(result).containsAllEntriesOf(completed);

		verify(first).apply(eq(context), anyMap());
		verify(second).apply(eq(context), eq(partial));
	}

	private SourceMappingContext context() {
		var item = JsonNodeFactory.instance.objectNode()
				.put("glyph_ref", "A1");

		return new SourceMappingContext(
				"Test Museum",
				"https://example.org/api",
				new SourceSample("items", List.of(item)),
				Set.of("glyph_ref")
		);
	}
}
