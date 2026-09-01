package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetPreparationResponse;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceResult;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSymbol;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.ApiIngestionResultResponse;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationSourceResultRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationSymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;


@Service
@RequiredArgsConstructor
public class DatasetPreparationService {

	private final DatasetPreparationRepository datasetPreparationRepository;
	private final DatasetPreparationSourceResultRepository sourceResultRepository;
	private final DatasetPreparationSymbolRepository datasetPreparationSymbolRepository;
	private final IngestionReviewItemRepository ingestionReviewItemRepository;

	@Transactional
	public DatasetPreparationResponse create(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Dataset preparation name is required");
		}

		DatasetPreparation preparation = DatasetPreparation.builder()
				.name(name.trim())
				.status(DatasetReadinessStatus.INGESTING)
				.statusReason("Dataset sources are being ingested")
				.build();

		DatasetPreparation saved = datasetPreparationRepository.save(preparation);

		return DatasetPreparationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public DatasetPreparationResponse get(Long datasetPreparationId) {
		return DatasetPreparationResponse.from(findPreparation(datasetPreparationId));
	}

	private DatasetPreparation findPreparation(Long datasetPreparationId) {
		if (datasetPreparationId == null || datasetPreparationId <= 0) {
			throw new IllegalArgumentException("A valid dataset preparation id is required");
		}

		return datasetPreparationRepository.findById(datasetPreparationId)
				.orElseThrow(() ->
						new IllegalArgumentException("No dataset preparation found for id " + datasetPreparationId));
	}

	@Transactional
	public DatasetPreparationSourceResult recordIngestionResult(Long datasetPreparationId, ApiIngestionResultResponse result) {
		if (result == null) {
			throw new IllegalArgumentException("Ingestion result is required");
		}

		DatasetPreparation preparation = findPreparation(datasetPreparationId);

		ensureStillIngesting(preparation);

		DatasetPreparationSourceResult sourceResult = findOrCreateSourceResult(preparation, result);

		recordImportedSymbols(preparation, result.createdSymbolIds());

		/*
		 * Deliberately DO NOT change the overall
		 * dataset readiness here.
		 *
		 * Maya may require review, but Egyptian,
		 * Coptic, Cuneiform, etc. still need to run.
		 */
		return sourceResult;
	}

	private DatasetPreparationSourceResult findOrCreateSourceResult(DatasetPreparation preparation, ApiIngestionResultResponse result) {
		if (result.ingestionJobId() != null) {
			var existing = sourceResultRepository.findByDatasetPreparationIdAndIngestionJobId(preparation.getId(), result.ingestionJobId());

			if (existing.isPresent()) {
				return existing.get();
			}
		}

		DatasetPreparationSourceResult sourceResult = DatasetPreparationSourceResult.builder()
				.datasetPreparation(preparation)
				.ingestionJobId(result.ingestionJobId())
				.sourceType(normaliseSourceType(result.sourceType()))
				.sourceName(normaliseSourceName(result.sourceName()))
				.sourcePath(result.sourcePath())
				.ingestionStatus(result.status())
				.importedCount(result.importedCount())
				.skippedCount(result.skippedCount())
				.manualProcessingCount(result.manualProcessingCount())
				.build();

		return sourceResultRepository.save(sourceResult);
	}

	private void recordImportedSymbols(DatasetPreparation preparation, List<Long> symbolIds) {
		if (symbolIds == null || symbolIds.isEmpty()) {
			return;
		}

		for (Long symboldId : symbolIds) {
			if (symboldId == null) {
				continue;
			}

			boolean exists = datasetPreparationSymbolRepository.existsByDatasetPreparationIdAndSymbolId(preparation.getId(), symboldId);

			if (exists) {
				continue;
			}

			DatasetPreparationSymbol datasetSymbol = DatasetPreparationSymbol.builder()
					.datasetPreparation(preparation)
					.symbolId(symboldId)
					.build();

			datasetPreparationSymbolRepository.save(datasetSymbol);
		}
	}

	@Transactional
	public DatasetPreparationSourceResult recordSourceFailure(Long datasetPreparationId, String sourceType, String sourceName, String sourcePath, String errorMessage) {
		DatasetPreparation preparation = findPreparation(datasetPreparationId);

		ensureStillIngesting(preparation);

		if (sourceName == null || sourceName.isBlank()) {
			throw new IllegalArgumentException("Source name is required");
		}

		DatasetPreparationSourceResult sourceResult = DatasetPreparationSourceResult.builder()
				.datasetPreparation(preparation)
				.sourceType(normaliseSourceType(sourceType))
				.sourceName(sourceName.trim())
				.sourcePath(sourcePath)
				.ingestionStatus(IngestionStatus.FAILED)
				.importedCount(0)
				.skippedCount(0)
				.manualProcessingCount(0)
				.errorMessage(errorMessage)
				.build();

		return sourceResultRepository.save(sourceResult);
	}

	@Transactional
	public DatasetPreparationResponse completeIngestion(Long datasetPreparationId) {
		DatasetPreparation preparation = findPreparation(datasetPreparationId);

		preparation.markIngestionComplete();

		datasetPreparationRepository.save(preparation);

		validateInternal(preparation);

		return DatasetPreparationResponse.from(preparation);
	}

	private void validateInternal(DatasetPreparation preparation) {
		preparation.markValidating();

		datasetPreparationRepository.save(preparation);

		List<DatasetPreparationSourceResult> sourceResults = sourceResultRepository.findAllByDatasetPreparationIdOrderByIdAsc(preparation.getId());

		if (sourceResults.isEmpty()) {
			preparation.markRetryRequired("No ingestion source results have been recorded");
			datasetPreparationRepository.save(preparation);
			return;
		}

		boolean failedSource = sourceResults.stream()
				.anyMatch(sourceResult ->
						sourceResult.getIngestionStatus() == IngestionStatus.FAILED
				);

		if (failedSource) {
			preparation.markRetryRequired(
					"One or more data sources failed and require retry"
			);

			datasetPreparationRepository.save(preparation);
			return;
		}

		long pendingReviewCount = countPendingReviewItems(sourceResults);

		if (pendingReviewCount > 0) {
			preparation.markReviewRequired(pendingReviewCount + " ingestion item(s) require human review");

			datasetPreparationRepository.save(preparation);
			return;
		}

		long symbolCount = datasetPreparationSymbolRepository.countByDatasetPreparationId(preparation.getId());

		if (symbolCount == 0) {
			preparation.markRetryRequired("Dataset contains no imported symbols");

			datasetPreparationRepository.save(preparation);
			return;
		}

		preparation.markReadyForMl();

		datasetPreparationRepository.save(preparation);
	}

	@Transactional
	public DatasetPreparationResponse revalidate(Long datasetPreparationId) {
		DatasetPreparation preparation = findPreparation(datasetPreparationId);

		if (preparation.getIngestionCompletedAt() == null) {
			throw new IllegalStateException("Dataset ingestion must be completed before validation");
		}

		validateInternal(preparation);

		return DatasetPreparationResponse.from(preparation);
	}

	@Transactional
	public DatasetPreparationResponse exclude(Long datasetPreparationId, String reason) {
		DatasetPreparation preparation = findPreparation(datasetPreparationId);

		preparation.exclude(reason);

		DatasetPreparation saved = datasetPreparationRepository.save(preparation);

		return DatasetPreparationResponse.from(saved);
	}

	private long countPendingReviewItems(List<DatasetPreparationSourceResult> sourceResults) {
		return sourceResults.stream()
				.map(DatasetPreparationSourceResult::getIngestionJobId)
				.filter(Objects::nonNull).mapToLong(ingestionJobId ->
						ingestionReviewItemRepository.countByIngestionJob_IdAndStatus(ingestionJobId, IngestionReviewStatus.PENDING))
				.sum();
	}

	private String normaliseSourceType(String sourceType) {
		if (sourceType == null || sourceType.isBlank()) {
			return "UNKNOWN";
		}

		return sourceType.trim();
	}

	private String normaliseSourceName(String sourceName) {
		if (sourceName == null || sourceName.isBlank()) {
			return "Unnamed source";
		}

		return sourceName.trim();
	}

	private void ensureStillIngesting(DatasetPreparation preparation) {
		if (preparation.getIngestionCompletedAt() != null) {
			throw new IllegalStateException("Cannot record new ingestion results after dataset ingestion has completed");
		}

		if (preparation.getStatus() != DatasetReadinessStatus.INGESTING) {
			throw new IllegalStateException("Dataset preparation is not ingesting");
		}
	}
}
