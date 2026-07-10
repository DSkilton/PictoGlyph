package com.pictoglyph.pictoglyphapi.ingestion.api;

public record SourceFieldMapping(
		String itemArrayField,
		String symbolCodeField,
		String imagePathField,
		String titleField,
		String descriptionField,
		String placeField,
		String periodField,
		String dateStartField,
		String dateEndField
) {
}
