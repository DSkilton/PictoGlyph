package com.pictoglyph.pictoglyphapi.ingestion.mapping.identifier;

import java.util.Objects;

public record SourceIdentifierAssessment(
		SourceIdentifierRole role,
		String reason
) {

	public SourceIdentifierAssessment {
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(reason, "reason");
	}

	public boolean eligibleForAutomaticMapping() {
		return role == SourceIdentifierRole.SIGN_CANDIDATE;
	}
}
