package com.pictoglyph.pictoglyphapi.agent.api;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentInvestigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentInvestigationController {

	private final AgentInvestigationService agentInvestigationService;

	@PostMapping("/investigate")
	public ResponseEntity<AgentInvestigationResponse> investigate(@Valid @RequestBody AgentInvestigationRequest request) {
		AgentContext context = AgentContext.builder()
				.symbolId(request.symbolId())
				.languageId(request.languageId())
				.placeId(request.placeId())
				.question(request.question())
				.build();

		AgentInvestigationResponse response = agentInvestigationService.investigateAndSave(context);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/investigations")
	public ResponseEntity<List<AgentInvestigationSummaryResponse>> getRecentInvestigations() {
		return ResponseEntity.ok(agentInvestigationService.findRecentInvestigations());
	}

	@GetMapping("/investigations/{investigationId}")
	public ResponseEntity<AgentInvestigationDetailResponse> getInvestigationById(@PathVariable Long investigationId) {
		return agentInvestigationService.findInvestigationById(investigationId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
