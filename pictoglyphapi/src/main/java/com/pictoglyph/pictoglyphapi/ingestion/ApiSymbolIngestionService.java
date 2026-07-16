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
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldValueReader;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
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

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.JsonNodePathReader.read;
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
	private final SourceMappingValidator sourceMappingValidator;
	private final SourceFieldValueReader sourceFieldValueReader;

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
			SourceFieldMapping mapping = request.sourceFieldMapping();
			List<JsonNode> candidateItems = findCandidateItems(rootNode, mapping.itemArrayField());

			SourceMappingValidationResult validationResult = sourceMappingValidator.validate(mapping, candidateItems);
			if (!validationResult.valid()) {
				throw new IllegalArgumentException("Invalid source field mapping: " + validationResult.errors());
			}

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
		SourceFieldMapping mapping = request.sourceFieldMapping();
		List<Long> createdSymbolIds = new ArrayList<>();
		List<ApiManualProcessingItemResponse> manualProcessingItems = new ArrayList<>();
		int skippedCount = 0;

		for (int index = 0; index < candidateItems.size(); index++) {
			JsonNode item = candidateItems.get(index);

			String rawSymbolCode = sourceFieldValueReader.readText(item, mapping.symbolCodeField());
			String imagePath = sourceFieldValueReader.readText(item, mapping.imagePathField());

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

				meta.set("sourceItem", item.deepCopy());
				meta.set("sourceFieldMapping", objectMapper.valueToTree(mapping));
				meta.put("originalImageUrl", downloadedImage.originalUrl());
				meta.put("downloadedImagePath", downloadedImage.localPath());
				meta.put("sourceType", SOURCE_TYPE);
				meta.put("sourceName", request.sourceName());
				meta.put("apiUrl", request.apiUrl());

				putMappedValue(meta, "title", item, mapping.titleField());
				putMappedValue(meta, "description", item, mapping.descriptionField());
				putMappedValue(meta, "place", item, mapping.placeField());
				putMappedValue(meta, "period", item, mapping.periodField());
				putMappedValue(meta, "dateStart", item, mapping.dateStartField());
				putMappedValue(meta, "dateEnd", item, mapping.dateEndField());

				Symbol symbol = Symbol.builder()
						.language(language)
						.symbolCode(symbolCode)
						.imagePath(downloadedImage.localPath())
						.meta(meta)
						.build();

				Symbol savedSymbol = symbolRepository.save(symbol);
				createdSymbolIds.add(savedSymbol.getId());

			} catch (RuntimeException exception) {
				manualProcessingItems.add(
						new ApiManualProcessingItemResponse(index, "Could not ingest symbol: " + exception.getMessage(), item.toString())
				);
			}
		}

		return new ApiIngestionStats(
				createdSymbolIds,
				skippedCount,
				manualProcessingItems
		);
	}

	private void putMappedValue(ObjectNode meta, String targetField, JsonNode sourceItem, String sourceFieldPath) {
		String value = sourceFieldValueReader.readText(sourceItem, sourceFieldPath);

		if (value != null) {
			meta.put(targetField, value);
		}
	}

	private List<JsonNode> findCandidateItems(JsonNode rootNode,String itemArrayField) {
		if (rootNode == null || rootNode.isNull()) {
			return List.of();
		}

		if (rootNode.isArray()) {
			return arrayNodeToList(rootNode);
		}

		if (itemArrayField == null && itemArrayField.isBlank()) {
			return rootNode.isObject() ? List.of(rootNode) : List.of();
		}

		JsonNode requestedArray = read(rootNode, itemArrayField);

		if (requestedArray == null && requestedArray.isNull()) {
			throw new IllegalArgumentException("Item array field was not found: " + itemArrayField);
		}

		if (!requestedArray.isArray()) {
			throw new IllegalArgumentException("Mapped item array field is not an array: " + itemArrayField);
		}

		return arrayNodeToList(requestedArray);
	}

	private List<JsonNode> arrayNodeToList(JsonNode arrayNode) {
		List<JsonNode> nodes = new ArrayList<>();
		Iterator<JsonNode> iterator = arrayNode.elements();

		while (iterator.hasNext()) {
			nodes.add(iterator.next());
		}

		return nodes;
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
