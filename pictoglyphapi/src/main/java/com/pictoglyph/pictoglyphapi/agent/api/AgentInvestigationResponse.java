package com.pictoglyph.pictoglyphapi.agent.api;

import com.pictoglyph.pictoglyphapi.agent.AgentResult;

public record AgentInvestigationResponse(
		Long investigationId,
		AgentResult result
) {
}