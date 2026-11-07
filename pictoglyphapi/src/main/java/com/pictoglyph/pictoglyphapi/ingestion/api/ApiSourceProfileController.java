package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.ingestion.ApiSourceProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ingestion/api-profiles")
@RequiredArgsConstructor
public class ApiSourceProfileController {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiSourceProfileController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final ApiSourceProfileService apiSourceProfileService;

	@PostMapping
	public ResponseEntity<ApiSourceProfileValidationResponse> createDraft(@Valid @RequestBody CreateApiSourceProfileRequest request) {
		LOGGER.info("Creating API source profile draft");

		ApiSourceProfileValidationResponse response;

		try {
			response = apiSourceProfileService.createDraft(request);
		} catch (RuntimeException e) {
			LOGGER.warn("API source profile draft creation failed: errorType={}", e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("API source profile draft created: profileId={}, status={}, warnings={}", response.profile().id(), response.profile().status(), response.warnings().size());
		PROVENANCE_LOGGER.info("event=api_source_profile_draft_created profileId={} status={}", response.profile().id(), response.profile().status());

		return ResponseEntity.ok(response);
	}

	@GetMapping
	public ResponseEntity<List<ApiSourceProfileResponse>> findAll() {
		LOGGER.debug("Retrieving API source profiles");

		return ResponseEntity.ok(apiSourceProfileService.findAll());
	}

	@GetMapping("/{profileId}")
	public ResponseEntity<ApiSourceProfileResponse> findById(@PathVariable Long profileId) {
		LOGGER.debug("Retrieving API source profile {}", profileId);

		return ResponseEntity.ok(apiSourceProfileService.findById(profileId));
	}

	@PostMapping("/{profileId}/approve")
	public ResponseEntity<ApiSourceProfileValidationResponse> approve(@PathVariable Long profileId) {
		LOGGER.info("Approving API source profile {}", profileId);

		ApiSourceProfileValidationResponse response;

		try {
			response = apiSourceProfileService.approve(profileId);
		} catch (RuntimeException e) {
			LOGGER.warn("API source profile approval failed: profileId={}, errorType={}", profileId, e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("API source profile approved: profileId={}, status={}, approvedFields={}", profileId, response.profile().status(), response.profile().approvedSchemaFields().size());
		PROVENANCE_LOGGER.info("event=api_source_profile_approved profileId={} status={} approvedFieldCount={}", profileId, response.profile().status(), response.profile().approvedSchemaFields().size());

		return ResponseEntity.ok(response);
	}

	@PostMapping("/{profileId}/run")
	public ResponseEntity<ApiIngestionResultResponse> run(@PathVariable Long profileId, @Valid @RequestBody RunApiSourceProfileRequest request) {
		LOGGER.info("Running API source profile: profileId={}, languageId={}", profileId, request.languageId());

		ApiIngestionResultResponse response;

		try {
			response = apiSourceProfileService.run(profileId, request);
		} catch (RuntimeException e) {
			LOGGER.warn("API source profile run failed: profileId={}, errorType={}", profileId, e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("API source profile run finished: profileId={}, jobId={}, status={}, imported={}, skipped={}, manualReview={}", profileId, response.ingestionJobId(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());
		PROVENANCE_LOGGER.info("event=api_source_profile_run_finished profileId={} languageId={} jobId={} status={} importedCount={} skippedCount={} manualProcessingCount={}", profileId, request.languageId(), response.ingestionJobId(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());

		return ResponseEntity.ok(response);
	}

}
