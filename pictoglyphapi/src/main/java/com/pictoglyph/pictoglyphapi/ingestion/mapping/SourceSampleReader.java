package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.JsonNodePathReader.read;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingCandidateCatalog.ITEM_ARRAY_FIELDS;

@Service
@RequiredArgsConstructor
public class SourceSampleReader {

	private static final int MAX_SAMPLE_ITEMS = 5;
	private static final int MAX_ARRAY_SEARCH_DEPTH = 3;

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	public SourceSample readSample(String apiUrl, String preferredItemArrayField) {
		try {
			String apiResponse = restTemplate.getForObject(apiUrl, String.class);

			if (apiResponse == null || apiResponse.isBlank()) {
				throw new IllegalStateException(
						"API returned an empty response: " + apiUrl
				);
			}

			JsonNode rootNode = objectMapper.readTree(apiResponse);

			if (rootNode == null || rootNode.isNull()) {
				throw new IllegalStateException(
						"API returned no readable JSON data: " + apiUrl
				);
			}

			String itemArrayField = findItemArrayField(rootNode, preferredItemArrayField);

			JsonNode itemNode = itemArrayField == null
					? rootNode
					: read(rootNode, itemArrayField);

			List<JsonNode> sampleItems = extractSampleItems(itemNode);

			return new SourceSample(
					itemArrayField,
					sampleItems
			);
		} catch (Exception exception) {
			throw new IllegalStateException(
					"Could not read source sample from: " + apiUrl,
					exception
			);
		}
	}

	private String findItemArrayField(JsonNode rootNode, String preferredItemArrayField) {
		if (rootNode.isArray()) {
			return null;
		}

		if (preferredItemArrayField != null && !preferredItemArrayField.isBlank()) {
			JsonNode preferredNode = read(rootNode, preferredItemArrayField);

			if (preferredNode != null && preferredNode.isArray()) {
				return preferredItemArrayField;
			}
		}

		for (String candidateField : ITEM_ARRAY_FIELDS) {
			String discoveredPath = findArrayPathByName(rootNode, "", candidateField, 0);

			if (discoveredPath != null) {
				return discoveredPath;
			}
		}

		return null;
	}

	private String findArrayPathByName(JsonNode node, String prefix, String candidateField, int depth) {
		if (node == null || !node.isObject() || depth > MAX_ARRAY_SEARCH_DEPTH) {
			return null;
		}

		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();

			String fieldName = field.getKey();
			JsonNode fieldValue = field.getValue();

			if (fieldName.equals(candidateField) && fieldValue.isArray()) {
				return buildPath(prefix, fieldName);
			}
		}

		fields = node.fields();

		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();

			if (!field.getValue().isObject()) {
				continue;
			}

			String childPrefix = buildPath(prefix, field.getKey());

			String discoveredPath = findArrayPathByName(field.getValue(), childPrefix, candidateField, depth + 1);

			if (discoveredPath != null) {
				return discoveredPath;
			}
		}

		return null;
	}

	private List<JsonNode> extractSampleItems(JsonNode itemNode) {
		if (itemNode == null || itemNode.isNull()) {
			return List.of();
		}

		if (!itemNode.isArray()) {
			return itemNode.isObject()
					? List.of(itemNode)
					: List.of();
		}

		List<JsonNode> sampleItems = new ArrayList<>();
		Iterator<JsonNode> iterator = itemNode.elements();

		while (iterator.hasNext()
				&& sampleItems.size() < MAX_SAMPLE_ITEMS) {

			JsonNode candidate = iterator.next();

			if (candidate != null && candidate.isObject()) {
				sampleItems.add(candidate);
			}
		}

		return sampleItems;
	}

	private String buildPath(String prefix, String fieldName) {
		return prefix == null || prefix.isBlank()
				? fieldName
				: prefix + "." + fieldName;
	}
}