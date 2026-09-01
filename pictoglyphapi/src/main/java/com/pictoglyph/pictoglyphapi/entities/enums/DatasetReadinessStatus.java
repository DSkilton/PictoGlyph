package com.pictoglyph.pictoglyphapi.entities.enums;

public enum DatasetReadinessStatus {
	INGESTING,
	REVIEW_REQUIRED,
	RETRY_REQUIRED,
	VALIDATING,
	READY_FOR_ML,
	EXCLUDED
}
