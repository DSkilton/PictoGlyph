package com.pictoglyph.pictoglyphapi.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationDetailResponse;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationResponse;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationSummaryResponse;
import com.pictoglyph.pictoglyphapi.entities.agent.AgentInvestigation;
import com.pictoglyph.pictoglyphapi.repositories.agent.AgentInvestigationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentInvestigationService {

	private final ContextAgent contextAgent;
	private final AgentInvestigationRepository agentInvestigationRepository;
	private final ObjectMapper objectMapper;

	public AgentInvestigationResponse investigateAndSave(AgentContext context) {
		AgentResult result = contextAgent.investigate(context);

		JsonNode resultJson = objectMapper.valueToTree(result);

		AgentInvestigation investigation = AgentInvestigation.builder()
				.symbolId(context.symbolId())
				.languageId(context.languageId())
				.placeId(context.placeId())
				.question(context.question())
				.resultJson(resultJson)
				.build();

		AgentInvestigation savedInvestigation = agentInvestigationRepository.save(investigation);

		return new AgentInvestigationResponse(
				savedInvestigation.getId(),
				result
		);
	}

	public List<AgentInvestigationSummaryResponse> findRecentInvestigations() {
		return agentInvestigationRepository.findTop20ByOrderByCreatedAtDesc()
				.stream()
				.map(this::toSummaryResponse)
				.toList();
	}

	private AgentInvestigationSummaryResponse toSummaryResponse(AgentInvestigation agentInvestigation) {
		return new AgentInvestigationSummaryResponse(
				agentInvestigation.getId(),
				agentInvestigation.getSymbolId(),
				agentInvestigation.getLanguageId(),
				agentInvestigation.getPlaceId(),
				agentInvestigation.getQuestion(),
				agentInvestigation.getCreatedAt()
		);
	}

	public Optional<AgentInvestigationDetailResponse> findInvestigationById(Long investigationId) {
		return agentInvestigationRepository.findById(investigationId)
				.map(this::toDetailResponse);
	}

	private AgentInvestigationDetailResponse toDetailResponse(AgentInvestigation agentInvestigation) {
		return new AgentInvestigationDetailResponse(
				agentInvestigation.getId(),
				agentInvestigation.getSymbolId(),
				agentInvestigation.getLanguageId(),
				agentInvestigation.getPlaceId(),
				agentInvestigation.getQuestion(),
				agentInvestigation.getResultJson(),
				agentInvestigation.getCreatedAt()
		);
	}
}