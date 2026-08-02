package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionReviewItemResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.UpdateIngestionReviewItemRequest;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service@RequiredArgsConstructor
public class IngestionReviewItemService {
	private final IngestionReviewItemRepository repository;

	@Transactional(readOnly = true)
	public List<IngestionReviewItemResponse> findByStatus(IngestionReviewStatus status) {
		return repository.findByStatusOrderByCreatedAtAsc(status)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<IngestionReviewItemResponse> findByJob(Long ingestionJobId) {
		return repository.findByIngestionJobIdOrderByItemIndexAsc(ingestionJobId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public IngestionReviewItemResponse update(Long reviewItemId, UpdateIngestionReviewItemRequest request) {
		IngestionReviewItem item = repository.findById(reviewItemId)
				.orElseThrow(() -> new IllegalArgumentException("No ingestion review item found for id: " + reviewItemId));

		if (request.status() == IngestionReviewStatus.PENDING) {
			throw new IllegalArgumentException("Review items can only be marked RESOLVED or DISMISSED");
		}

		item.setStatus(request.status());
		item.setResolutionNotes(cleanNullable(request.resolutionNotes()));
		item.setResolvedAt(LocalDateTime.now());

		return toResponse(repository.save(item));
	}

	private String cleanNullable(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private IngestionReviewItemResponse toResponse(IngestionReviewItem item) {
		return new IngestionReviewItemResponse(
				item.getId(),
				item.getIngestionJob().getId(),
				item.getItemIndex(),
				item.getReason(),
				item.getRawItem(),
				item.getStatus(),
				item.getResolutionNotes(),
				item.getCreatedAt(),
				item.getResolvedAt()
		);
	}

}


