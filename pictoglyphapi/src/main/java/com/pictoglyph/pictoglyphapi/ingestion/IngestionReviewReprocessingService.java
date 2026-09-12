package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.dataset.DatasetPreparationService;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import com.pictoglyph.pictoglyphapi.ingestion.api.ReprocessIngestionReviewItemRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ReprocessIngestionReviewItemResponse;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IngestionReviewReprocessingService {
	private final IngestionReviewItemRepository reviewItemRepository;

	private final ApiSymbolIngestionService apiSymbolIngestionService;

	private final DatasetPreparationService datasetPreparationService;

	@Transactional
	public ReprocessIngestionReviewItemResponse reprocess(Long reviewItemId, ReprocessIngestionReviewItemRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Reprocessing request is required");
		}

		IngestionReviewItem reviewItem = reviewItemRepository.findById(reviewItemId)
				.orElseThrow(() -> new IllegalArgumentException("No ingestion review item found for " + reviewItemId));

		if (reviewItem.getStatus() != IngestionReviewStatus.PENDING) {
			throw new IllegalStateException("Only PENDING review items can be reprocessed");
		}

		Long ingestionJobId = reviewItem.getIngestionJob().getId();
		String apiUrl = reviewItem.getIngestionJob().getSourcePath();
		JsonNode correctedItem = request.correctedItem().deepCopy();

		/*
		 * If this fails, the exception propagates
		 * and the review item remains PENDING
		 */

		Symbol savedSymbol = apiSymbolIngestionService.reprocessReviewedItem(request.languageId(), request.sourceName(), apiUrl, request.sourceFieldMapping(), correctedItem);
		LocalDateTime now = LocalDateTime.now();

		reviewItem.setCorrectedItem(correctedItem);
		reviewItem.setReprocessedSymbolId(savedSymbol.getId());
		reviewItem.setReprocessedAt(now);
		reviewItem.setStatus(IngestionReviewStatus.RESOLVED);
		reviewItem.setResolutionNotes(cleanNullable(request.resolutionNotes()));
		reviewItem.setResolvedAt(now);

		IngestionReviewItem savedReviewItem = reviewItemRepository.saveAndFlush(reviewItem);

		datasetPreparationService.recordReviewedSymbolForIngestionJob(ingestionJobId, savedSymbol.getId());
		datasetPreparationService.revalidateForIngestionJob(ingestionJobId);

		return new ReprocessIngestionReviewItemResponse(
				savedReviewItem.getId(),
				ingestionJobId,
				savedSymbol.getId(),
				savedReviewItem.getStatus(),
				savedReviewItem.getRawItem(),
				savedReviewItem.getCorrectedItem(),
				savedReviewItem.getResolutionNotes(),
				savedReviewItem.getReprocessedAt()
		);

	}

	private String cleanNullable(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}
}
