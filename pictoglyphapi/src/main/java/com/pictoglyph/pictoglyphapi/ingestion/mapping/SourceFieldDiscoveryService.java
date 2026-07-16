package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SourceFieldDiscoveryService {

	private static final int MAX_NESTED_DEPTH = 3;

	public Set<String> discoverFields(List<JsonNode> sampleItems) {
		Set<String> discoveredFields = new LinkedHashSet<>();

		if (sampleItems == null) {
			return discoveredFields;
		}

		for (JsonNode sampleItem : sampleItems) {
			discoverFields(sampleItem, "", discoveredFields, 0
			);
		}

		return discoveredFields;
	}

	private void discoverFields(JsonNode node, String prefix, Set<String> discoveredFields, int depth) {
		if (node == null || node.isNull() || !node.isObject() || depth > MAX_NESTED_DEPTH) {
			return;
		}

		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();

			String fieldPath = buildPath(prefix, field.getKey());
			JsonNode value = field.getValue();

			if (value == null || value.isNull()) {
				continue;
			}

			if (value.isObject()) {
				discoverFields(value, fieldPath, discoveredFields, depth + 1);

				continue;
			}

			if (isSupportedScalar(value)) {
				discoveredFields.add(fieldPath);
			}
		}
	}

	private boolean isSupportedScalar(JsonNode node) {
		return node.isTextual()
				|| node.isNumber()
				|| node.isBoolean();
	}

	private String buildPath(String prefix, String fieldName) {
		return prefix == null || prefix.isBlank()
				? fieldName
				: prefix + "." + fieldName;
	}
}