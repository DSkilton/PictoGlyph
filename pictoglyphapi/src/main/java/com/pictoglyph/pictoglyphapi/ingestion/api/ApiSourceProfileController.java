package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.ingestion.ApiSourceProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

	private final ApiSourceProfileService apiSourceProfileService;

	@PostMapping
	public ResponseEntity<ApiSourceProfileValidationResponse> createDraft(@Valid @RequestBody CreateApiSourceProfileRequest request) {
		return ResponseEntity.ok(apiSourceProfileService.createDraft(request));
	}

	@GetMapping
	public ResponseEntity<List<ApiSourceProfileResponse>> findAll() {
		return ResponseEntity.ok(apiSourceProfileService.findAll());
	}

	@GetMapping("/{profileId}")
	public ResponseEntity<ApiSourceProfileResponse> findById(@PathVariable Long profileId) {
		return ResponseEntity.ok(apiSourceProfileService.findById(profileId));
	}

	@PostMapping("/{profileId}/approve")
	public ResponseEntity<ApiSourceProfileValidationResponse> approve(@PathVariable Long profileId) {
		return ResponseEntity.ok(apiSourceProfileService.approve(profileId));
	}

	@PostMapping("/{profileId}/run")
	public ResponseEntity<ApiIngestionResultResponse> run(@PathVariable Long profileId, @Valid @RequestBody RunApiSourceProfileRequest request) {
		return ResponseEntity.ok(apiSourceProfileService.run(profileId, request));
	}

}
