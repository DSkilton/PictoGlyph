package com.pictoglyph.pictoglyphapi.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.ml.api.MlApiContractVersions;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import org.springframework.stereotype.Component;

@Component
public class MlProcessingRequestFactory {

	public MlProcessingRequest create(MlProcessingJob job, Symbol symbol) {
		if (job == null) {
			throw new IllegalArgumentException("Ml processing job is required");
		}

		if (symbol == null) {
			throw new IllegalArgumentException("Symbol is required");
		}

		if (job.getId() == null) {
			throw new IllegalArgumentException("Ml processing job must have an id");
		}

		if (symbol.getId() == null) {
			throw new IllegalArgumentException("Symbol must have an id");
		}

		if (!symbol.getId().equals(job.getSymbolId())) {
			throw new IllegalArgumentException("Symbol does not match Ml processing");
		}

		String imagePath = requiredText(symbol.getImagePath(), "Symbol image path is required");
		String inputChecksum = requiredText(job.getInputChecksum(), "Ml processing job input checksum is required");

		Long languageId = symbol.getLanguage() == null
				? null
				: symbol.getLanguage().getId();

		JsonNode metadata = symbol.getMeta();

		return new MlProcessingRequest(
				MlApiContractVersions.V1,
				job.getId(),
				job.getSymbolId(),
				job.getTaskType(),
				job.getModelProfile(),
				imagePath,
				inputChecksum,
				symbol.getSymbolCode(),
				languageId,
				metadata
		);
	}

	private String requiredText(String value, String message ){
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
