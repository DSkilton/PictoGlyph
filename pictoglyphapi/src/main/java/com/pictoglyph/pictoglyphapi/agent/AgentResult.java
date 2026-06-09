package com.pictoglyph.pictoglyphapi.agent;

import lombok.Builder;

import java.util.List;

@Builder
public record AgentResult (List<Hypothesis> hypotheses, List<Evidence> evidence){

}
