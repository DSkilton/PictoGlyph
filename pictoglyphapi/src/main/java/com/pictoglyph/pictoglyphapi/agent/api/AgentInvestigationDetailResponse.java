package com.pictoglyph.pictoglyphapi.agent.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AgentInvestigationDetailResponse(
		Long investigationId,
		Long symbolId,
		Long languageId,
		Long placeId,
		String questions,
		JsonNode result,
		LocalDateTime createdAt
) {
}
