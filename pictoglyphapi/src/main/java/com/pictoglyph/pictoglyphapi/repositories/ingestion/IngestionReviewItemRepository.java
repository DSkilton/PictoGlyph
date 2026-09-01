package com.pictoglyph.pictoglyphapi.repositories.ingestion;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionReviewItemRepository extends JpaRepository<IngestionReviewItem, Long> {

	List<IngestionReviewItem> findByStatusOrderByCreatedAtAsc(IngestionReviewStatus status);

	List<IngestionReviewItem> findByIngestionJobIdOrderByItemIndexAsc(Long ingestionJobId);

	// underscore is required i.e. ingestionJob.id
	long countByIngestionJob_IdAndStatus(Long ingestionJobId, IngestionReviewStatus status);
}
