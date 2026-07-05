package com.pictoglyph.pictoglyphapi.ingestion.api.ingestion.api;

import com.pictoglyph.pictoglyphapi.ingestion.api.FolderIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.FolderSymbolIngestionService;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

	private final FolderSymbolIngestionService folderSymbolIngestionService;

	@PostMapping("/folders")
	public ResponseEntity<IngestionResultResponse> ingestFolder(@Valid @RequestBody FolderIngestionRequest request) {
		IngestionResultResponse response = folderSymbolIngestionService.ingestFolder(
				request.languageId(),
				request.folderPath()
		);

		return ResponseEntity.ok(response);
	}


}
