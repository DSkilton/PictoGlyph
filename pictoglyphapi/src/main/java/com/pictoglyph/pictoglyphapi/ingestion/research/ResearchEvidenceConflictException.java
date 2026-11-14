package com.pictoglyph.pictoglyphapi.ingestion.research;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ResearchEvidenceConflictException extends RuntimeException {

	public ResearchEvidenceConflictException(String message) {
		super(message);
	}
}
