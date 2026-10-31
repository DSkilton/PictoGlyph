package com.pictoglyph.pictoglyphapi.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;

public final class SignEligibilityPolicy {

	private SignEligibilityPolicy() {	}

	public static boolean isApprovedSignCrop(Symbol symbol) {
		if (symbol == null || symbol.getMeta() == null) {
			return false;
		}

		JsonNode meta = symbol.getMeta();

		return "SIGN_CROP".equals(meta.path("contentType").asText()) &&
				"APPROVED".equals(meta.path("reviewStatus").asText()) &&
				!meta.path("signLabel").asText().isBlank();
	}
}
