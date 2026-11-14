
package com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy;

import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;

import java.util.Map;

public interface SourceMappingStrategy {

	String id();

	MappingStep apply(SourceMappingContext context, Map<SourceMappingTarget, FieldMatch> currentMatches);
}
