package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewItemService;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewReprocessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionReviewController {
	private static final Logger LOGGER = LoggerFactory.getLogger(IngestionReviewController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private final IngestionReviewItemService reviewItemService;
	private final IngestionReviewReprocessingService reprocessingService;


	@GetMapping("/review-items")
	public ResponseEntity<List<IngestionReviewItemResponse>> findByStatus(@RequestParam(defaultValue = "PENDING") IngestionReviewStatus status) {
		LOGGER.debug("Retrieving ingestion review items: status={}", status);

		return ResponseEntity.ok(reviewItemService.findByStatus(status));
	}

	@GetMapping("/jobs/{jobId}/review-items")
	public ResponseEntity<List<IngestionReviewItemResponse>> findByJob(@PathVariable("jobId") Long jobId) {
		LOGGER.debug("Retrieving ingestion review items for job {}", jobId);

		return ResponseEntity.ok(reviewItemService.findByJob(jobId));
	}

	@PatchMapping("/review-items/{reviewItemId}")
	public ResponseEntity<IngestionReviewItemResponse> update(@PathVariable("reviewItemId") Long reviewItemId, @Valid @RequestBody UpdateIngestionReviewItemRequest request) {
		LOGGER.info("Updating ingestion review item: reviewItemId={}", reviewItemId);

		IngestionReviewItemResponse response;

		try {
			response = reviewItemService.update(reviewItemId, request);
		} catch (RuntimeException e) {
			LOGGER.warn("Ingestion review update failed: reviewItemId={}, errorType={}", reviewItemId, e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("Ingestion review item updated: reviewItemId={}, status={}", reviewItemId, response.status());
		PROVENANCE_LOGGER.info("event=review_item_dismissed reviewItemId={} status={}", reviewItemId, response.status());

		return ResponseEntity.ok(response);
	}

	@PostMapping("/review-items/{reviewItemId}/reprocess")
	public ResponseEntity<ReprocessIngestionReviewItemResponse> reprocess(@PathVariable("reviewItemId") Long reviewItemId, @Valid @RequestBody ReprocessIngestionReviewItemRequest request) {
		LOGGER.info("Starting ingestion review reprocessing: reviewItemId={}", reviewItemId);

		ReprocessIngestionReviewItemResponse response;

		try {
			response = reprocessingService.reprocess(reviewItemId, request);
		} catch (RuntimeException e) {
			LOGGER.warn("Ingestion review reprocessing failed: reviewItemId={}, errorType={}", reviewItemId, e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("Ingestion review reprocessed: reviewItemId={}, jobId={}, symbolId={}, status={}", response.reviewItemId(), response.ingestionJobId(), response.symbolId(), response.status());
		PROVENANCE_LOGGER.info("event=review_item_reprocessed reviewItemId={} jobId={} symbolId={} status={}", response.reviewItemId(), response.ingestionJobId(), response.symbolId(), response.status());

		return ResponseEntity.ok(response);
	}

}
