package com.pictoglyph.pictoglyphapi.ingestion.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResearchSourceRecordRepository extends JpaRepository<ResearchSourceRecord, Long> {

	Optional<ResearchSourceRecord> findBySourceIdAndRecordKey(String sourceId, String recordKey);
}
