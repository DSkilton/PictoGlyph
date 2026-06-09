package com.pictoglyph.pictoglyphapi.agent;

import java.util.List;

public interface AgentTool {

	String getName();
	List<Evidence> execute(AgentContext context);
}
