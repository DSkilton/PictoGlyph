package com.pictoglyph.pictoglyphapi.dataset.api;

import com.pictoglyph.pictoglyphapi.entities.DatasetPreparation.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;

import java.time.LocalDateTime;

public record DatasetPreparationResponse(
		Long id,
		String name,
		DatasetReadinessStatus status,
		String statusReason,
		LocalDateTime ingestionCompletedAt,
		LocalDateTime validatedAt,
		LocalDateTime readyForMlAt,
		LocalDateTime excludedAt,
		String exclusionReason,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static DatasetPreparationResponse from(DatasetPreparation preparation) {
		return new DatasetPreparationResponse(
				preparation.getId(),
				preparation.getName(),
				preparation.getStatus(),
				preparation.getStatusReason(),
				preparation.getIngestionCompletedAt(),
				preparation.getValidatedAt(),
				preparation.getReadyForMlAt(),
				preparation.getExcludedAt(),
				preparation.getExclusionReason(),
				preparation.getCreatedAt(),
				preparation.getUpdatedAt()
		);
	}
}
