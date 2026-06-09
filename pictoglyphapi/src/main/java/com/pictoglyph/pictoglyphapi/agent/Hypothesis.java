package com.pictoglyph.pictoglyphapi.agent;

import java.util.List;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import lombok.Builder;

@Builder
public record Hypothesis(String conclusion, double confidence, List<Evidence> supportingEvidence, List<Evidence> contradictingEvidence) {
}
