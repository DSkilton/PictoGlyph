package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LanguageTool implements AgentTool {

	@Override
	public String getName() {
		return "LanguageTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		return List.of(
				Evidence.builder()
						.source(getName())
						.description("Mock Language evidence.")
						.confidence(0.70)
						.build()
		);
	}
}
