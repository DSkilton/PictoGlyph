package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonNodePathReader {

	private JsonNodePathReader() {	}

	public static JsonNode read(JsonNode node, String path) {
		if (node == null || node.isNull() || path == null || path.isBlank()) {
			return null;
		}

		JsonNode current = node;

		for (String pathPart : path.split("\\.")) {
			if (current == null || current.isNull() | !current.isObject()) {
				return null;
			}

			current = current.get(pathPart);
		}

		return current;
	}
}
