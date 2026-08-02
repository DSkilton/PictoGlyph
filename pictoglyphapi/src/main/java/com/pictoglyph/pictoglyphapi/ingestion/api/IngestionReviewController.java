package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionReviewController {

	private final IngestionReviewItemService reviewItemService;

	@GetMapping("/review-items")
	private ResponseEntity<List<IngestionReviewItemResponse>> findByStatus(@RequestParam(defaultValue = "PENDING")IngestionReviewStatus status) {
		return ResponseEntity.ok(reviewItemService.findByStatus(status));
	}

	@GetMapping("/jobs/{jobId}/review-items")
	private ResponseEntity<List<IngestionReviewItemResponse>> findByJob(@PathVariable Long jobId) {
		return ResponseEntity.ok(reviewItemService.findByJob(jobId));
	}

	@PatchMapping
	public ResponseEntity<IngestionReviewItemResponse> update(@PathVariable Long reviewItemId, @Valid @RequestBody UpdateIngestionReviewItemRequest request) {
		return ResponseEntity.ok(
				reviewItemService.update(reviewItemId, request)
		);
	}



}
