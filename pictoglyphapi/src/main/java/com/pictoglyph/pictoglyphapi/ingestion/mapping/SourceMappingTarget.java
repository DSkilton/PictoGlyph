package com.pictoglyph.pictoglyphapi.ingestion.mapping;

public enum SourceMappingTarget {
	SYMBOL_CODE("symbolCodeField", true, 1.5),
	IMAGE_PATH("imagePathField", true, 1.5),
	TITLE("titleField", false, 1.0),
	DESCRIPTION("descriptionField", false, 1.0),
	PLACE("placeField", false, 1.0),
	PERIOD("periodField", false, 1.0),
	DATE_START("dateStartField", false, 1.0),
	DATE_END("dateEndField", false, 1.0);

	private final String responseFieldName;
	private final boolean requiredForImport;
	private final double confidenceWeight;

	SourceMappingTarget(String responseFieldName, boolean requiredForImport, double confidenceWeight) {
		this.responseFieldName = responseFieldName;
		this.requiredForImport = requiredForImport;
		this.confidenceWeight = confidenceWeight;
	}

	public String responseFieldName() {
		return responseFieldName;
	}

	public boolean requiredForImport() {
		return requiredForImport;
	}

	public double confidenceWeight() {
		return confidenceWeight;
	}
}