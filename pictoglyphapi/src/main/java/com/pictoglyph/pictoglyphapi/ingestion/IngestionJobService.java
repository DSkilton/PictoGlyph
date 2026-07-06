package com.pictoglyph.pictoglyphapi.ingestion;


import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionJobSummaryResponse;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionJobService {
	private final IngestionJobRepository ingestionJobRepository;

	public List<IngestionJobSummaryResponse> findRecentJobs() {
		return ingestionJobRepository.findTop20ByOrderByCreatedAtDesc()
				.stream()
				.map(this::toSummaryResponse)
				.toList();
	}

	private IngestionJobSummaryResponse toSummaryResponse(IngestionJob job) {
		return new IngestionJobSummaryResponse(
			job.getId(),
			job.getSourceType(),
			job.getSourcePath(),
			job.getStatus(),
			job.getImportedCount(),
			job.getSkippedCount(),
			job.getManualProcessingCount(),
			job.getCreatedAt(),
			job.getCompletedAt()
		);
	}
}
