package com.pictoglyph.pictoglyphapi.repositories.agent;

import com.pictoglyph.pictoglyphapi.entities.agent.AgentInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentInvestigationRepository extends JpaRepository<AgentInvestigation, Long>  {

	List<AgentInvestigation> findTop20ByOrderByCreatedAtDesc();
}
