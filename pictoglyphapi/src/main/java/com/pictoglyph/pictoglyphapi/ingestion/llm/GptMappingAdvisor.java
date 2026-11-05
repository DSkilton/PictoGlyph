package com.pictoglyph.pictoglyphapi.ingestion.llm;

import java.util.List;
import java.util.Set;

public interface GptMappingAdvisor {

	List<GptFieldSuggestion> suggest(
			String sourceName,
			Set<String> discoveredFields
	);
}
