package com.pictoglyph.pictoglyphapi.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContextAgent {

	private static final double NO_EVIDENCE_CONFIDENCE = 0.0;
	private static final String DEFAULT_HYPOTHESIS_CONCLUSION = "Most likely interpretation requires further evidence.";

	private final List<AgentTool> tools;

	public AgentResult investigate(AgentContext context) {
		List<Evidence> evidence = collectEvidence(context);
		List<Evidence> sortedEvidence = sortEvidenceByConfidence(evidence);
		Hypothesis hypothesis = buildHypothesis(sortedEvidence);

		return AgentResult.builder()
				.evidence(sortedEvidence)
				.hypotheses(List.of(hypothesis))
				.build();
	}

	private List<Evidence> collectEvidence(AgentContext context) {
		List<Evidence> evidence = new ArrayList<>();

		for (AgentTool tool : tools) {
			evidence.addAll(tool.execute(context));
		}

		return evidence;
	}

	private List<Evidence> sortEvidenceByConfidence(List<Evidence> evidence) {
		return evidence.stream()
				.sorted(Comparator.comparingDouble(Evidence::confidence).reversed())
				.toList();
	}

	private Hypothesis buildHypothesis(List<Evidence> sortedEvidence) {
		return Hypothesis.builder()
				.conclusion(DEFAULT_HYPOTHESIS_CONCLUSION)
				.confidence(calculateAverageConfidence(sortedEvidence))
				.supportingEvidence(sortedEvidence)
				.contradictingEvidence(List.of())
				.build();
	}

	private double calculateAverageConfidence(List<Evidence> evidence) {
		if (evidence.isEmpty()) {
			return NO_EVIDENCE_CONFIDENCE;
		}

		return evidence.stream()
				.mapToDouble(Evidence::confidence)
				.average()
				.orElse(NO_EVIDENCE_CONFIDENCE);
	}
}