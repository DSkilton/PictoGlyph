package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiManualProcessingItemResponse;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.pictoglyph.pictoglyphapi.utils.StringUtils.*;

@Service
@RequiredArgsConstructor
public class ApiSymbolIngestionService {

	private static final String SOURCE_TYPE = "API";

	private final LanguageRepository languageRepository;
	private final SymbolRepository symbolRepository;
	private final IngestionJobRepository ingestionJobRepository;
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final RemoteImageStorageService remoteImageStorageService;

	public ApiIngestionResultResponse ingestApi(ApiIngestionRequest request) {
		IngestionJob ingestJob = createRunningJob(request);

		try {
			Language language = languageRepository.findById(request.languageId())
					.orElseThrow(() -> new IllegalArgumentException("No language found for id: " + request.languageId()
			));

			String apiResponse = restTemplate.getForObject(request.apiUrl(), String.class);

			if (apiResponse == null || apiResponse.isBlank()) {
				throw new IllegalStateException("API returned an emtpy response");
			}

			JsonNode rootNode = objectMapper.readTree(apiResponse);
			List<JsonNode> candidateItems = findCandidateItems(rootNode, request.itemArrayField());

			ApiIngestionStats stats = processCandidateItems(
					language,
					request.languageId(),
					request,
					candidateItems
			);

			IngestionStatus finalStatus = stats.manualProcessingItems().isEmpty()
					? IngestionStatus.COMPLETED
					: IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING;

			completeJob(
					ingestJob,
					finalStatus,
					stats.createdSymbolsIds().size(),
					stats.skippedCount(),
					stats.manualProcessingItems().size(),
					null
			);

			return buildResponse(request,ingestJob, stats);
		} catch (Exception exception) {
			failJob(ingestJob, exception);
			throw new IllegalStateException("API ingestion failed for: " + request.apiUrl(), exception);
		}
	}

	private ApiIngestionStats processCandidateItems(Language language, Long languageId, ApiIngestionRequest request, List<JsonNode> candidateItems) {
		List<Long> createdSymbolIds = new ArrayList<>();
		List<ApiManualProcessingItemResponse> manualProcessingItems = new ArrayList<>();
		int skippedCount = 0;

		for (int index = 0; index < candidateItems.size(); index++) {
			JsonNode item = candidateItems.get(index);

			String rawSymbolCode = extractSymbolCode(item, request.symbolCodeField());
			String imagePath = extractImagePath(item, request.imagePathField());

			if (rawSymbolCode == null || rawSymbolCode.isBlank()) {
				manualProcessingItems.add(
						new ApiManualProcessingItemResponse(index, "Missing symbol code", item.toString())
				);
				continue;
			}

			if (imagePath == null || imagePath.isBlank()) {
				manualProcessingItems.add(
						new ApiManualProcessingItemResponse(index, "Missing image path or image URL", item.toString())
				);
				continue;
			}

			String symbolCode = cleanString(rawSymbolCode);

			if (symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(languageId, symbolCode)) {
				skippedCount++;
				continue;
			}

			try {
				DownloadedImage downloadedImage = remoteImageStorageService.downloadedImage(imagePath, SOURCE_TYPE, languageId, symbolCode);

				ObjectNode meta = objectMapper.createObjectNode();
				meta.set("sourceItem", item);
				meta.put("originalImageUrl", downloadedImage.originalUrl());
				meta.put("downloadedImagePath", downloadedImage.localPath());
				meta.put("sourceType", SOURCE_TYPE);
				meta.put("sourceName", request.sourceName());
				meta.put("apiIrl", request.apiUrl());

				Symbol symbol = Symbol.builder()
						.language(language)
						.symbolCode(symbolCode)
						.imagePath(downloadedImage.localPath())
						.meta(item)
						.build();

				Symbol savedSymbol = symbolRepository.save(symbol);
				createdSymbolIds.add(savedSymbol.getId());

			} catch (RuntimeException exception) {
				manualProcessingItems.add(
						new ApiManualProcessingItemResponse(index, "Could not save symbol: " + exception.getMessage(), item.toString())
				);
			}
		}

		return new ApiIngestionStats(
				createdSymbolIds,
				skippedCount,
				manualProcessingItems
		);
	}

	private List<JsonNode> findCandidateItems(JsonNode rootNode,String itemArrayField) {
		if (rootNode == null || rootNode.isNull()) {
			return List.of();
		}

		if (rootNode.isArray()) {
			return arrayNodeToList(rootNode);
		}

		if (itemArrayField != null && !itemArrayField.isBlank()) {
			JsonNode requestedArray = rootNode.get(itemArrayField);

			if (requestedArray != null && requestedArray.isArray()) {
				return arrayNodeToList(requestedArray);
			}
		}

		for (String defaultField : List.of("symbols", "items", "results", "data")) {
			JsonNode possibleArray = rootNode.get(defaultField);

			if (possibleArray != null && possibleArray.isArray()) {
				return arrayNodeToList(possibleArray);
			}
		}

		return List.of(rootNode);
	}

	private List<JsonNode> arrayNodeToList(JsonNode arrayNode) {
		List<JsonNode> nodes = new ArrayList<>();
		Iterator<JsonNode> iterator = arrayNode.elements();

		while (iterator.hasNext()) {
			nodes.add(iterator.next());
		}

		return nodes;
	}

	private String extractSymbolCode(JsonNode item, String preferredField) {
		String preferredValue = extractText(item, preferredField);

		List<String> values = List.of("symbolCode", "symbol_code", "code", "id", "name", "title", "gardinerCode");

		if (preferredValue != null) {
			return preferredValue;
		}

		for (String field : values) {
			String value = extractText(item, field);

			if (value != null) {
				return value;
			}
		}

		return null;
	}

	private String extractImagePath(JsonNode item, String preferredField) {
		String preferredValue = extractText(item, preferredField);

		List<String> values = List.of("imagePath", "image_path", "imageUrl", "image_url", "image", "thumbnailUrl", "thumbnail_url", "url");

		if (preferredValue != null) {
			return preferredValue;
		}

		for (String field : values) {
			String value = extractText(item, field);

			if (value != null) {
				return value;
			}
		}

		return null;
	}

	private String extractText(JsonNode item, String fieldName) {
		if (item == null || fieldName == null || fieldName.isBlank()) {
			return null;
		}

		JsonNode valueNode = item.get(fieldName);

		if (valueNode == null || valueNode.isNull()) {
			return null;
		}

		if (valueNode.isTextual()) {
			String value = valueNode.asText();

			return value.isBlank() ? null : value;
		}

		if (valueNode.isNumber()) {
			return valueNode.asText();
		}

		return null;
	}

	private IngestionJob createRunningJob(ApiIngestionRequest request) {
		IngestionJob ingestionJob = IngestionJob.builder()
				.sourceType(SOURCE_TYPE)
				.sourcePath(request.apiUrl())
				.status(IngestionStatus.RUNNING)
				.importedCount(0)
				.skippedCount(0)
				.manualProcessingCount(0)
				.build();

		return ingestionJobRepository.save(ingestionJob);
	}

	private void completeJob(IngestionJob ingestionJob, IngestionStatus status, int importedCount, int skippedCount, int manualProcessingCount, String errorMessage) {
		ingestionJob.setStatus(status);
		ingestionJob.setImportedCount(importedCount);
		ingestionJob.setSkippedCount(skippedCount);
		ingestionJob.setManualProcessingCount(manualProcessingCount);
		ingestionJob.setErrorMessage(errorMessage);
		ingestionJob.setCompletedAt(LocalDateTime.now());

		ingestionJobRepository.save(ingestionJob);
	}

	private void failJob(IngestionJob ingestionJob, Exception exception) {
		completeJob(
				ingestionJob,
				IngestionStatus.FAILED,
				ingestionJob.getImportedCount(),
				ingestionJob.getSkippedCount(),
				ingestionJob.getManualProcessingCount(),
				exception.getMessage()
		);
	}

	private ApiIngestionResultResponse buildResponse(ApiIngestionRequest request, IngestionJob ingestionJob, ApiIngestionStats stats) {
		return new ApiIngestionResultResponse(
				ingestionJob.getId(),
				SOURCE_TYPE,
				request.sourceName(),
				request.apiUrl(),
				ingestionJob.getStatus(),
				stats.createdSymbolsIds().size(),
				stats.skippedCount(),
				stats.manualProcessingItems().size(),
				stats.createdSymbolsIds(),
				stats.manualProcessingItems()
		);
	}
}
