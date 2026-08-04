package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobQueueService;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportedSymbolPersistenceService {

	private final SymbolRepository symbolRepository;
	private final MlProcessingJobQueueService mlProcessingJobQueueService;

	@Transactional
	public Symbol saveAndQueueImageEmbedding(Symbol symbol, String modelProfile, String inputChecksum) {
		if (symbol == null) {
			throw new IllegalArgumentException("Symbol is required");
		}

		Symbol savedSymbol = symbolRepository.save(symbol);

		if (savedSymbol.getId() == null) {
			throw new IllegalStateException("Imported symbol was saved without an id");
		}

		mlProcessingJobQueueService.queueImageEmbedding(savedSymbol.getId(), modelProfile, inputChecksum);

		return savedSymbol;
	}
}
