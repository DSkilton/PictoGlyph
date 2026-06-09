package com.pictoglyph.pictoglyphapi.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

		return AgentResult.builder()
				.hypotheses(List.of())
				.build();
	}

}
