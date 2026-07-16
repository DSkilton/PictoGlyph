package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SourceMappingConfidenceCalculator {

	public double calculate(Map<SourceMappingTarget, FieldMatch> matches) {
		if (matches == null || matches.isEmpty()) {
			return 0.0;
		}

		double weightedTotal = 0.0;
		double totalWeight = 0.0;

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			FieldMatch match = matches.get(target);

			if (match == null || match.sourceField() == null) {
				if (target.requiredForImport()) {
					totalWeight += target.confidenceWeight();
				}

				continue;
			}

			double weight = target.confidenceWeight();

			weightedTotal += match.confidence() * weight;
			totalWeight += weight;
		}

		if (totalWeight == 0.0) {
			return 0.0;
		}

		return round(weightedTotal / totalWeight);
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}