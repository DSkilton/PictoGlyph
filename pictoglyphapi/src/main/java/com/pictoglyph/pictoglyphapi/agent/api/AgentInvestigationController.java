package com.pictoglyph.pictoglyphapi.agent.api;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentInvestigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentInvestigationController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AgentInvestigationController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private final AgentInvestigationService agentInvestigationService;

	@PostMapping("/investigate")
	public ResponseEntity<AgentInvestigationResponse> investigate(@Valid @RequestBody AgentInvestigationRequest request) {
		LOGGER.info("Starting agent investigation: symbolId={}, languageId={}, placeId={}", request.symbolId(), request.languageId(), request.placeId());

		AgentContext context = AgentContext.builder()
				.symbolId(request.symbolId())
				.languageId(request.languageId())
				.placeId(request.placeId())
				.question(request.question())
				.build();

		AgentInvestigationResponse response;

		try {
			response = agentInvestigationService.investigateAndSave(context);
		} catch (RuntimeException e) {
			LOGGER.warn("Agent investigation failed: symbolId={}, errorType={}", request.symbolId(), e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("Agent investigation saved: investigationId={}, symbolId={}", response.investigationId(), request.symbolId());
		PROVENANCE_LOGGER.info("event=agent_investigation_created investigationId={} symbolId={} languageId={} placeId={}", response.investigationId(), request.symbolId(), request.languageId(), request.placeId());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/investigations")
	public ResponseEntity<List<AgentInvestigationSummaryResponse>> getRecentInvestigations() {
		LOGGER.debug("Retrieving recent agent investigations");

		return ResponseEntity.ok(agentInvestigationService.findRecentInvestigations());
	}

	@GetMapping("/investigations/{investigationId}")
	public ResponseEntity<AgentInvestigationDetailResponse> getInvestigationById(@PathVariable Long investigationId) {
		LOGGER.debug("Retrieving agent investigation {}", investigationId);

		return agentInvestigationService.findInvestigationById(investigationId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
