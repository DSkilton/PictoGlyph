package com.pictoglyph.pictoglyphapi.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContextAgent {

	private final List<AgentTool> tools;

	public AgentResult investigate(AgentContext context) {
		List<Evidence> evidence = new ArrayList<>();

		for (AgentTool tool : tools) {
			evidence.addAll(tool.execute(context));
		}

		List<Evidence> sortedEvidence = evidence.stream()
				.sorted(Comparator.comparingDouble(Evidence::confidence).reversed())
				.toList();

		Hypothesis hypothesis = Hypothesis.builder()
				.conclusion("Most likely interpretation requires further evidence.")
				.confidence(calculateAverageConfidence(sortedEvidence))
				.supportingEvidence(sortedEvidence)
				.contradictingEvidence(List.of())
				.build();

		return AgentResult.builder()
				.evidence(sortedEvidence)
				.hypotheses(List.of(hypothesis))
				.build();
	}

	private double calculateAverageConfidence(List<Evidence> evidence) {
		if (evidence.isEmpty()) {
			return 0.0;
		}

		return evidence.stream()
				.mapToDouble(Evidence::confidence)
				.average()
				.orElse(0.0);
	}
}
