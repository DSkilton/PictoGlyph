package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.JsonNodePathReader.read;

@Component
public class SourceFieldValueReader {

	public String readText(JsonNode item, String fieldPath) {
		JsonNode valueNode = read(item, fieldPath);

		if (valueNode == null || valueNode.isNull()) {
			return null;
		}

		if (!valueNode.isTextual() && !valueNode.isNumber() && !valueNode.isBoolean()) {
			return null;
		}

		String value = valueNode.asText().trim();

		return value.isBlank() ? null : value;
	}
}
