package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DateTool implements AgentTool {
	@Override
	public String getName() {
		return "DateTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		return List.of(
				Evidence.builder()
						.source(getName())
						.description("Mock date evidence")
						.confidence(0.75)
						.build()
		);
	}
}
