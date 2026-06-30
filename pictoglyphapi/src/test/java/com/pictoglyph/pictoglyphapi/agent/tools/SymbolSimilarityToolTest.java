package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityClient;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityMatch;
import com.pictoglyph.pictoglyphapi.agent.ml.SymbolSimilarityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SymbolSimilarityToolTest {

	@Mock
	private SymbolSimilarityClient symbolSimilarityClient;

	@InjectMocks
	private SymbolSimilarityTool symbolSimilarityTool;

	@Test
	void executeShouldReturnNoEvidenceWhenSymbolIdIsNull() {
		AgentContext context = AgentContext.builder()
				.symbolId(null)
				.question("What symbols are visually similar?")
				.build();

		List<Evidence> evidence = symbolSimilarityTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(symbolSimilarityClient);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenMlResponseIsNull() {
		AgentContext context = contextWithSymbolId(1L);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(null);

		List<Evidence> evidence = symbolSimilarityTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence symbolEvidence = evidence.get(0);

		assertThat(symbolEvidence.source()).isEqualTo("SymbolSimilarityTool");
		assertThat(symbolEvidence.description())
				.isEqualTo("No similar symbols found for symbol id: 1");
		assertThat(symbolEvidence.confidence()).isEqualTo(0.10);

		verify(symbolSimilarityClient).findSimilarSymbols(1L);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenMlResponseHasNullMatches() {
		AgentContext context = contextWithSymbolId(1L);

		SymbolSimilarityResponse response = new SymbolSimilarityResponse(
				1L,
				"mock-symbol-similarity-v1",
				null
		);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(response);

		List<Evidence> evidence = symbolSimilarityTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence symbolEvidence = evidence.get(0);

		assertThat(symbolEvidence.source()).isEqualTo("SymbolSimilarityTool");
		assertThat(symbolEvidence.description())
				.isEqualTo("No similar symbols found for symbol id: 1");
		assertThat(symbolEvidence.confidence()).isEqualTo(0.10);

		verify(symbolSimilarityClient).findSimilarSymbols(1L);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenMlResponseHasEmptyMatches() {
		AgentContext context = contextWithSymbolId(1L);

		SymbolSimilarityResponse response = new SymbolSimilarityResponse(
				1L,
				"mock-symbol-similarity-v1",
				List.of()
		);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(response);

		List<Evidence> evidence = symbolSimilarityTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence symbolEvidence = evidence.get(0);

		assertThat(symbolEvidence.source()).isEqualTo("SymbolSimilarityTool");
		assertThat(symbolEvidence.description())
				.isEqualTo("No similar symbols found for symbol id: 1");
		assertThat(symbolEvidence.confidence()).isEqualTo(0.10);

		verify(symbolSimilarityClient).findSimilarSymbols(1L);
	}

	@Test
	void executeShouldMapHighSimilarityToHighConfidence() {
		List<Evidence> evidence = executeWithSingleMatch(0.91);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).confidence()).isEqualTo(0.85);
	}

	@Test
	void executeShouldMapMediumSimilarityToMediumConfidence() {
		List<Evidence> evidence = executeWithSingleMatch(0.87);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).confidence()).isEqualTo(0.75);
	}

	@Test
	void executeShouldMapLowSimilarityToLowConfidence() {
		List<Evidence> evidence = executeWithSingleMatch(0.74);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).confidence()).isEqualTo(0.60);
	}

	@Test
	void executeShouldMapWeakSimilarityToWeakConfidence() {
		List<Evidence> evidence = executeWithSingleMatch(0.69);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).confidence()).isEqualTo(0.35);
	}

	@Test
	void executeShouldUseLowConfidenceWhenSimilarityIsNull() {
		List<Evidence> evidence = executeWithSingleMatch(null);

		assertThat(evidence).hasSize(1);
		assertThat(evidence.get(0).confidence()).isEqualTo(0.10);
	}

	@Test
	void executeShouldMapThresholdValuesInclusively() {
		SymbolSimilarityResponse response = new SymbolSimilarityResponse(
				1L,
				"mock-symbol-similarity-v1",
				List.of(
						new SymbolSimilarityMatch(2L, 0.90),
						new SymbolSimilarityMatch(3L, 0.80),
						new SymbolSimilarityMatch(4L, 0.70)
				)
		);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(response);

		List<Evidence> evidence = symbolSimilarityTool.execute(contextWithSymbolId(1L));

		assertThat(evidence).hasSize(3);

		assertThat(evidence.get(0).confidence()).isEqualTo(0.85);
		assertThat(evidence.get(1).confidence()).isEqualTo(0.75);
		assertThat(evidence.get(2).confidence()).isEqualTo(0.60);

		verify(symbolSimilarityClient).findSimilarSymbols(1L);
	}

	@Test
	void executeShouldReturnEvidenceForEachSimilarityMatch() {
		SymbolSimilarityResponse response = new SymbolSimilarityResponse(
				1L,
				"mock-symbol-similarity-v1",
				List.of(
						new SymbolSimilarityMatch(2L, 0.87),
						new SymbolSimilarityMatch(3L, 0.81),
						new SymbolSimilarityMatch(4L, 0.74)
				)
		);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(response);

		List<Evidence> evidence = symbolSimilarityTool.execute(contextWithSymbolId(1L));

		assertThat(evidence).hasSize(3);

		assertThat(evidence)
				.extracting(Evidence::source)
				.containsExactly(
						"SymbolSimilarityTool",
						"SymbolSimilarityTool",
						"SymbolSimilarityTool"
				);

		assertThat(evidence)
				.extracting(Evidence::description)
				.containsExactly(
						"Symbol 1 is visually similar to symbol 2 using model mock-symbol-similarity-v1. Similarity score: 0.87.",
						"Symbol 1 is visually similar to symbol 3 using model mock-symbol-similarity-v1. Similarity score: 0.81.",
						"Symbol 1 is visually similar to symbol 4 using model mock-symbol-similarity-v1. Similarity score: 0.74."
				);

		assertThat(evidence)
				.extracting(Evidence::confidence)
				.containsExactly(0.75, 0.75, 0.60);

		verify(symbolSimilarityClient).findSimilarSymbols(1L);
	}

	private List<Evidence> executeWithSingleMatch(Double similarity) {
		SymbolSimilarityResponse response = new SymbolSimilarityResponse(
				1L,
				"mock-symbol-similarity-v1",
				List.of(new SymbolSimilarityMatch(2L, similarity))
		);

		when(symbolSimilarityClient.findSimilarSymbols(1L)).thenReturn(response);

		List<Evidence> evidence = symbolSimilarityTool.execute(contextWithSymbolId(1L));

		verify(symbolSimilarityClient).findSimilarSymbols(1L);

		return evidence;
	}

	private AgentContext contextWithSymbolId(Long symbolId) {
		return AgentContext.builder()
				.symbolId(symbolId)
				.question("What symbols are visually similar?")
				.build();
	}
}