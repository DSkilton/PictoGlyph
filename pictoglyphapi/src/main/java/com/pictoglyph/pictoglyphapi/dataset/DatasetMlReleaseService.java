package com.pictoglyph.pictoglyphapi.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlResponse;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSymbol;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.ingestion.ImageChecksumService;
import com.pictoglyph.pictoglyphapi.ml.MlJobQueueResult;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobQueueService;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationSymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.pictoglyph.pictoglyphapi.ingestion.ImageChecksumService.CHECKSUM_ALGORITHM;

@Service
@RequiredArgsConstructor
public class DatasetMlReleaseService {

	private final DatasetPreparationRepository datasetPreparationRepository;
	private final DatasetPreparationSymbolRepository datasetPreparationSymbolRepository;
	private final SymbolRepository symbolRepository;
	private final ImageChecksumService imageChecksumService;
	private final MlProcessingJobQueueService mlProcessingJobQueueService;

	@Transactional
	public ReleaseDatasetToMlResponse release(Long datasetPreparationId, String modelProfile) {
		DatasetPreparation preparation = requireReadyDataset(datasetPreparationId);
		String cleanModelProfile = requiredModelProfile(modelProfile);

		List<DatasetPreparationSymbol> datasetSymbols = datasetPreparationSymbolRepository.findAllByDatasetPreparationIdOrderBySymbolIdAsc(preparation.getId());

		if (datasetSymbols.isEmpty()) {
			throw new IllegalStateException("Dataset contains no symbols to release to ML");
		}

		List<VerifiedSymbol> verifiedSymbols = verifySymbols(datasetSymbols);

		int createdJobCount = 0;
		int existingJobCount = 0;

		for (VerifiedSymbol verifiedSymbol : verifiedSymbols) {
			MlJobQueueResult result = mlProcessingJobQueueService.queueImageEmbedding(verifiedSymbol.symbol().getId(), cleanModelProfile, verifiedSymbol.checkSum());

			if (result.created()) {
				createdJobCount++;
			} else {
				existingJobCount++;
			}
		}

		return new ReleaseDatasetToMlResponse(preparation.getId(), cleanModelProfile, verifiedSymbols.size(), createdJobCount, existingJobCount);
	}

	private List<VerifiedSymbol> verifySymbols(List<DatasetPreparationSymbol> datasetSymbols) {
		List<VerifiedSymbol> verifiedSymbols = new ArrayList<>();

		for (DatasetPreparationSymbol datasetSymbol : datasetSymbols) {
			Symbol symbol = symbolRepository.findById(datasetSymbol.getSymbolId())
					.orElseThrow(() ->
							new IllegalStateException("Dataset references missing symbol " + datasetSymbol.getSymbolId()));

			String storedChecksum = requireStoredChecksum(symbol);
			String currentChecksum = imageChecksumService.calculateSha256((symbol.getImagePath()));

			if (!storedChecksum.equalsIgnoreCase(currentChecksum)) {
				throw new IllegalStateException("Image checksum mismatch for symbol " + symbol.getId());
			}

			verifiedSymbols.add(new VerifiedSymbol(symbol, currentChecksum));
		}

		return List.copyOf(verifiedSymbols);
	}

	private String requireStoredChecksum(Symbol symbol) {
		JsonNode metadata = symbol.getMeta();

		if (metadata == null || metadata.isNull()) {
			throw new IllegalStateException("Symbol " + symbol.getId() + " does not contain provenance metadata");
		}

		String storedChecksum = metadata.path("imageChecksum").asText().trim();
		String checksumAlgorithm = metadata.path("imageChecksumAlgorithm").asText().trim();

		if (storedChecksum.isBlank()) {
			throw new IllegalStateException("Symbol " + symbol.getId() + " does not contain an image checksum");
		}

		if (!CHECKSUM_ALGORITHM.equalsIgnoreCase(checksumAlgorithm)) {
			throw new IllegalStateException("Symbol " + symbol.getId() + " does not use the expected checksum algorithm");
		}

		return storedChecksum;
	}

	private String requiredModelProfile(String modelProfile) {
		if (modelProfile == null || modelProfile.isBlank()) {
			throw new IllegalArgumentException("ML model profile is required");
		}

		return modelProfile.trim();
	}

	private DatasetPreparation requireReadyDataset(Long datasetPreparationId) {
		if (datasetPreparationId == null || datasetPreparationId <= 0) {
			throw new IllegalArgumentException("A valid dataset preparation id is required");
		}

		DatasetPreparation preparation = datasetPreparationRepository.findById(datasetPreparationId)
				.orElseThrow(() ->
						new IllegalArgumentException(("No dataset preparation found for id: " + datasetPreparationId)));

		if (preparation.getStatus() != DatasetReadinessStatus.READY_FOR_ML) {
			throw new IllegalStateException("Dataset must be " + DatasetReadinessStatus.READY_FOR_ML.toString() + " before it can be released");
		}

		return preparation;
	}

	private record VerifiedSymbol(
			Symbol symbol,
			String checkSum
	) {

	}
}
