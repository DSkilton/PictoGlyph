package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.ingestion.ApiSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.FolderSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionJobService;
import com.pictoglyph.pictoglyphapi.ingestion.SourceMappingProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

	private final FolderSymbolIngestionService folderSymbolIngestionService;
	private final IngestionJobService ingestionJobService;
	private final ApiSymbolIngestionService apiSymbolIngestionService;
	private final SourceMappingProposalService sourceMappingProposalService;

	@PostMapping("/folders")
	public ResponseEntity<IngestionResultResponse> ingestFolder(@Valid @RequestBody FolderIngestionRequest request) {
		IngestionResultResponse response = folderSymbolIngestionService.ingestFolder(
				request.languageId(),
				request.folderPath()
		);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/apis")
	public ResponseEntity<ApiIngestionResultResponse> ingestApi(@Valid @RequestBody ApiIngestionRequest request) {
		ApiIngestionResultResponse response = apiSymbolIngestionService.ingestApi(request);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/jobs")
	public ResponseEntity<List<IngestionJobSummaryResponse>> getRecentJobs() {
		return ResponseEntity.ok(ingestionJobService.findRecentJobs());
	}

	@PostMapping("/mappings/propose")
	public ResponseEntity<SourceMappingProposalResponse> proposeMapping(@Valid @RequestBody SourceMappingProposalRequest request){
		SourceMappingProposalResponse response = sourceMappingProposalService.proposeMapping(request);

		return ResponseEntity.ok(response);
	}

}
