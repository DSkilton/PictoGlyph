package com.pictoglyph.pictoglyphapi.utils;

import java.util.Locale;

public class StringUtils {

	public static String cleanString(String input) {
		return input
				.trim()
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9_-]", "_")
				.replaceAll("_+", "_");
	}
}
