package com.pictoglyph.pictoglyphapi.repositories.ingestion;

import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

	List<IngestionJob> findTop20ByOrderByCreatedAtDesc();

}
