package com.pictoglyph.pictoglyphapi.agent.api;

import jakarta.validation.constraints.Size;

public record AgentInvestigationRequest (Long symbolId, Long languageId, Long placeId, @Size(max=1000, message = "Question must be 1000 or fewer")String question) {

}
