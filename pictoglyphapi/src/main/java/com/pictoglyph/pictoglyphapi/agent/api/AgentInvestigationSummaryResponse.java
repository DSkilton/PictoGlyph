package com.pictoglyph.pictoglyphapi.agent.api;

import java.time.LocalDateTime;

public record AgentInvestigationSummaryResponse(
		Long investigationId,
		Long symbolId,
		Long languageId,
		Long placeId,
		String question,
		LocalDateTime createdAt
) {
}
