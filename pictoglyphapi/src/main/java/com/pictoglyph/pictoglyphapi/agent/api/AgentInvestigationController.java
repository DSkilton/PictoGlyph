package com.pictoglyph.pictoglyphapi.agent.api;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentResult;
import com.pictoglyph.pictoglyphapi.agent.ContextAgent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentInvestigationController {

	private final ContextAgent contextAgent;

	@PostMapping("/investigate")
	public ResponseEntity<AgentResult> investigate(@Valid @RequestBody AgentInvestigationRequest request) {
		AgentContext context = AgentContext.builder()
				.symbolId(request.symbolId())
				.languageId(request.languageId())
				.placeId(request.placeId())
				.question(request.question())
				.build();

		AgentResult result = contextAgent.investigate(context);

		return ResponseEntity.ok(result);
	}
}
