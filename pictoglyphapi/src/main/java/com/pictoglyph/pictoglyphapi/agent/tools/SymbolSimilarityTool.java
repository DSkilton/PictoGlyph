package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;

import java.util.List;

public class SymbolSimilarityTool implements AgentTool {
	@Override
	public String getName() {
		return "SymbolSimilarityTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		return List.of(
				Evidence.builder()
						.source(getName())
						.description("Mock symbol similarity evidence")
						.confidence(0.85)
						.build()
		);
	}
}
