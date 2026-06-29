package com.pictoglyph.pictoglyphapi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationDetailResponse;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationResponse;
import com.pictoglyph.pictoglyphapi.agent.api.AgentInvestigationSummaryResponse;
import com.pictoglyph.pictoglyphapi.entities.agent.AgentInvestigation;
import com.pictoglyph.pictoglyphapi.repositories.agent.AgentInvestigationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentInvestigationServiceTest {

	public static final String WHAT_IS_THIS_SYMBOL = "What is this symbol?";
	public static final String SUMMARY = "summary";
	public static final String FAKE_INVESTIGATION_RESULT = "Fake investigation result";
	public static final String WHAT_IS_THE_LIKELY_CONTEXT = "What is the likely context?";
	@Mock
	private ContextAgent contextAgent;

	@Mock
	private AgentInvestigationRepository agentInvestigationRepository;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private AgentInvestigationService agentInvestigationService;

	@Test
	void investigateAndSaveShouldRunAgentAndPersistInvestigation() {
		AgentContext context = AgentContext.builder()
				.symbolId(1L)
				.languageId(2L)
				.placeId(3L)
				.question(WHAT_IS_THE_LIKELY_CONTEXT)
				.build();

		Evidence evidence = Evidence.builder()
				.source("LanguageTool")
				.description("Fake language evidence")
				.confidence(0.75)
				.build();

		Hypothesis hypothesis = Hypothesis.builder()
				.conclusion("Further evidence required.")
				.confidence(0.75)
				.supportingEvidence(List.of(evidence))
				.contradictingEvidence(List.of())
				.build();

		AgentResult result = AgentResult.builder()
				.evidence(List.of(evidence))
				.hypotheses(List.of(hypothesis))
				.build();

		when(contextAgent.investigate(context)).thenReturn(result);

		when(agentInvestigationRepository.save(any(AgentInvestigation.class)))
				.thenAnswer(invocation -> {
					AgentInvestigation investigation = invocation.getArgument(0);
					investigation.setId(10L);
					return investigation;
				});

		AgentInvestigationResponse response =
				agentInvestigationService.investigateAndSave(context);

		assertThat(response.investigationId()).isEqualTo(10L);
		assertThat(response.result()).isEqualTo(result);

		ArgumentCaptor<AgentInvestigation> investigationCaptor =
				ArgumentCaptor.forClass(AgentInvestigation.class);

		verify(agentInvestigationRepository).save(investigationCaptor.capture());

		AgentInvestigation savedInvestigation = investigationCaptor.getValue();

		assertThat(savedInvestigation.getSymbolId()).isEqualTo(1L);
		assertThat(savedInvestigation.getLanguageId()).isEqualTo(2L);
		assertThat(savedInvestigation.getPlaceId()).isEqualTo(3L);
		assertThat(savedInvestigation.getQuestion()).isEqualTo(WHAT_IS_THE_LIKELY_CONTEXT);
		assertThat(savedInvestigation.getResultJson()).isNotNull();
		assertThat(savedInvestigation.getResultJson().get("evidence")).isNotNull();

		verify(contextAgent).investigate(context);
	}

	@Test
	void findRecentInvestigationsShouldReturnSummaryResponses() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 6, 29, 12, 0);

		AgentInvestigation investigation = AgentInvestigation.builder()
				.id(1L)
				.symbolId(2L)
				.languageId(3L)
				.placeId(4L)
				.question(WHAT_IS_THIS_SYMBOL)
				.createdAt(createdAt)
				.build();

		when(agentInvestigationRepository.findTop20ByOrderByCreatedAtDesc())
				.thenReturn(List.of(investigation));

		List<AgentInvestigationSummaryResponse> responses =
				agentInvestigationService.findRecentInvestigations();

		assertThat(responses).hasSize(1);

		AgentInvestigationSummaryResponse response = responses.get(0);

		assertThat(response.investigationId()).isEqualTo(1L);
		assertThat(response.symbolId()).isEqualTo(2L);
		assertThat(response.languageId()).isEqualTo(3L);
		assertThat(response.placeId()).isEqualTo(4L);
		assertThat(response.question()).isEqualTo(WHAT_IS_THIS_SYMBOL);
		assertThat(response.createdAt()).isEqualTo(createdAt);

		verify(agentInvestigationRepository).findTop20ByOrderByCreatedAtDesc();
	}

	@Test
	void findInvestigationByIdShouldReturnDetailResponseWhenFound() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 6, 29, 12, 30);

		ObjectNode resultJson = objectMapper.createObjectNode();
		resultJson.put(SUMMARY, FAKE_INVESTIGATION_RESULT);

		AgentInvestigation investigation = AgentInvestigation.builder()
				.id(1L)
				.symbolId(2L)
				.languageId(3L)
				.placeId(4L)
				.question(WHAT_IS_THIS_SYMBOL)
				.resultJson(resultJson)
				.createdAt(createdAt)
				.build();

		when(agentInvestigationRepository.findById(1L))
				.thenReturn(Optional.of(investigation));

		Optional<AgentInvestigationDetailResponse> response =
				agentInvestigationService.findInvestigationById(1L);

		assertThat(response).isPresent();

		AgentInvestigationDetailResponse detail = response.get();

		assertThat(detail.investigationId()).isEqualTo(1L);
		assertThat(detail.symbolId()).isEqualTo(2L);
		assertThat(detail.languageId()).isEqualTo(3L);
		assertThat(detail.placeId()).isEqualTo(4L);
		assertThat(detail.questions()).isEqualTo(WHAT_IS_THIS_SYMBOL);
		assertThat(detail.result().get(SUMMARY).asText()).isEqualTo(FAKE_INVESTIGATION_RESULT);
		assertThat(detail.createdAt()).isEqualTo(createdAt);

		verify(agentInvestigationRepository).findById(1L);
	}

	@Test
	void findInvestigationByIdShouldReturnEmptyWhenNotFound() {
		when(agentInvestigationRepository.findById(99L))
				.thenReturn(Optional.empty());

		Optional<AgentInvestigationDetailResponse> response =
				agentInvestigationService.findInvestigationById(99L);

		assertThat(response).isEmpty();

		verify(agentInvestigationRepository).findById(99L);
	}
}