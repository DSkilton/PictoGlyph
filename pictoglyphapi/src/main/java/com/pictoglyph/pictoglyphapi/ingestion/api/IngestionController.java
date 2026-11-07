package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.FolderSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionJobService;
import com.pictoglyph.pictoglyphapi.ingestion.SourceMappingProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(IngestionController.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger("PICTOGLYPH_PROVENANCE");

	private final FolderSymbolIngestionService folderSymbolIngestionService;
	private final IngestionJobService ingestionJobService;
	private final ApiSymbolIngestionService apiSymbolIngestionService;
	private final SourceMappingProposalService sourceMappingProposalService;


	@PostMapping("/folders")
	public ResponseEntity<IngestionResultResponse> ingestFolder(@Valid @RequestBody FolderIngestionRequest request) {
		LOGGER.info("Starting folder ingestion: languageId={}", request.languageId());

		IngestionResultResponse response;

		try {
			response = folderSymbolIngestionService.ingestFolder(request.languageId(), request.folderPath());
		} catch (RuntimeException e) {
			LOGGER.warn("Folder ingestion failed: languageId={}, errorType={}", request.languageId(), e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("Folder ingestion completed: jobId={}, status={}, imported={}, skipped={}, manualReview={}", response.ingestionJobId(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());
		PROVENANCE_LOGGER.info("event=folder_ingestion_completed jobId={} languageId={} status={} importedCount={} skippedCount={} manualProcessingCount={}", response.ingestionJobId(), request.languageId(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());

		return ResponseEntity.ok(response);
	}

	@PostMapping("/apis")
	public ResponseEntity<ApiIngestionResultResponse> ingestApi(@Valid @RequestBody ApiIngestionRequest request) {
		LOGGER.info("Starting API ingestion: languageId={}", request.languageId());

		ApiIngestionResultResponse response;

		try {
			response = apiSymbolIngestionService.ingestApi(request);
		} catch (RuntimeException e) {
			LOGGER.warn("API ingestion failed: languageId={}, errorType={}", request.languageId(), e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("API ingestion completed: jobId={}, status={}, imported={}, skipped={}, manualReview={}", response.ingestionJobId(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());
		PROVENANCE_LOGGER.info("event=api_ingestion_completed jobId={} sourceType={} status={} importedCount={} skippedCount={} manualProcessingCount={}", response.ingestionJobId(), response.sourceType(), response.status(), response.importedCount(), response.skippedCount(), response.manualProcessingCount());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/jobs")
	public ResponseEntity<List<IngestionJobSummaryResponse>> getRecentJobs() {
		LOGGER.debug("Retrieving recent ingestion jobs");

		return ResponseEntity.ok(ingestionJobService.findRecentJobs());
	}

	@PostMapping("/mappings/propose")
	public ResponseEntity<SourceMappingProposalResponse> proposeMapping(@Valid @RequestBody SourceMappingProposalRequest request) {
		LOGGER.info("Generating source mapping proposal");

		SourceMappingProposalResponse response;

		try {
			response = sourceMappingProposalService.proposeMapping(request);
		} catch (RuntimeException e) {
			LOGGER.warn("Source mapping proposal failed: errorType={}", e.getClass().getSimpleName());
			throw e;
		}

		LOGGER.info("Source mapping proposal completed: sampledItems={}, discoveredFields={}, confidence={}", response.sampledItemCount(), response.discoveredFields().size(), response.overallConfidence());

		return ResponseEntity.ok(response);
	}

}
