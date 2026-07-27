package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfileStatus;

import java.time.LocalDateTime;

public record ApiSourceProfileResponse(
		Long id,
		String profileName,
		String sourceName,
		String apiUrl,
		ApiSourceProfileStatus status,
		SourceFieldMapping sourceFieldMapping,
		LocalDateTime createdAt,
		LocalDateTime udpatedAt,
		LocalDateTime validatedAt,
		LocalDateTime approvedAt
) {
}
