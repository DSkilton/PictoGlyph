package com.pictoglyph.pictoglyphapi.ingestion.research;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/ingestion/research-records")
@RequiredArgsConstructor
public class ResearchRecordController {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResearchRecordController.class);

	private final ResearchRecordService service;

	@Value("${pictoglyph.research-import.enabled:false}")
	private boolean enabled;

	@Value("${pictoglyph.research-import.token:}")
	private String importToken;

	@PostMapping
	public ResponseEntity<ResearchRecordResponse> importRecord(@RequestHeader(value = "X-PictoGlyph-Import-Token", required = false) String suppliedToken, @Valid @RequestBody ResearchRecordRequest request) {
		if (!enabled) {
			LOGGER.warn("event=research_import_access_rejected reason=IMPORT_DISABLED");
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Research record import is disabled");
		}

		if (importToken == null || importToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(importToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
			LOGGER.warn("event=research_import_access_rejected reason=INVALID_OR_MISSING_TOKEN");
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid import token");
		}

		try {
			ResearchRecordResponse result = service.importRecord(request);

			LOGGER.debug("event=research_import_http_completed sourceId={} recordKey={} recordId={} created={}",
					request.sourceId(), request.recordKey(), result.recordId(), result.created());

			return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
		} catch (ResearchEvidenceConflictException exception) {
			throw exception;

		} catch (RuntimeException exception) {
			LOGGER.error("event=research_import_http_failed sourceId={} recordKey={} errorType={}",
					request.sourceId(), request.recordKey(), exception.getClass().getSimpleName(), exception);
			throw exception;
		}
	}
}
