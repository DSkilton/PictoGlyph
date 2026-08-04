package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApiSourceProfileResponse(
		Long id,
		String profileName,
		String sourceName,
		String apiUrl,
		ApiSourceProfileStatus status,
		SourceFieldMapping sourceFieldMapping,
		List<String> approvedSchemaFields,
		LocalDateTime createdAt,
		LocalDateTime udpatedAt,
		LocalDateTime validatedAt,
		LocalDateTime approvedAt
) {

	public ApiSourceProfileResponse {
		approvedSchemaFields = approvedSchemaFields == null
				? List.of()
				: List.copyOf(approvedSchemaFields);
	}
}
