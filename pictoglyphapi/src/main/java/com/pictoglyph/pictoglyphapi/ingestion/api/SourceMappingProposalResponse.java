package com.pictoglyph.pictoglyphapi.ingestion.api;

import java.util.List;

public record SourceMappingProposalResponse(
		String sourceName,
		String apiUrl,
		SourceFieldMapping proposedMapping,
		double overallConfidence,
		int sampledItemCount,
		List<String> discoveredFields,
		List<MappingEvidenceResponse> evidence
) {
}
