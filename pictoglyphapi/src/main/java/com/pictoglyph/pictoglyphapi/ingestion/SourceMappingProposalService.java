package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.ingestion.api.MappingEvidenceResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalResponse;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldMatcher;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingConfidenceCalculator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSampleReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DATE_END;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DATE_START;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.DESCRIPTION;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.IMAGE_PATH;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.PERIOD;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.PLACE;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.SYMBOL_CODE;
import static com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget.TITLE;

@Service
@RequiredArgsConstructor
public class SourceMappingProposalService {

	private final SourceSampleReader sourceSampleReader;
	private final SourceFieldDiscoveryService sourceFieldDiscoveryService;
	private final SourceFieldMatcher sourceFieldMatcher;
	private final SourceMappingConfidenceCalculator confidenceCalculator;

	public SourceMappingProposalResponse proposeMapping(SourceMappingProposalRequest request) {
		SourceSample sourceSample = sourceSampleReader.readSample(request.apiUrl(), request.itemArrayField());

		Set<String> discoveredFields = sourceFieldDiscoveryService.discoverFields(sourceSample.sampleItems());

		Map<SourceMappingTarget, FieldMatch> matches = sourceFieldMatcher.matchFields(discoveredFields, sourceSample.sampleItems());

		com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping proposedMapping = buildSourceFieldMapping(sourceSample.itemArrayField(), matches);

		return new SourceMappingProposalResponse(
				request.sourceName(),
				request.apiUrl(),
				proposedMapping,
				confidenceCalculator.calculate(matches),
				sourceSample.sampleItems().size(),
				new ArrayList<>(discoveredFields),
				buildEvidence(matches)
		);
	}

	private com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping buildSourceFieldMapping(String itemArrayField, Map<SourceMappingTarget, FieldMatch> matches) {
		return new com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping(
				itemArrayField,
				sourceField(matches, SYMBOL_CODE),
				sourceField(matches, IMAGE_PATH),
				sourceField(matches, TITLE),
				sourceField(matches, DESCRIPTION),
				sourceField(matches, PLACE),
				sourceField(matches, PERIOD),
				sourceField(matches, DATE_START),
				sourceField(matches, DATE_END)
		);
	}

	private List<MappingEvidenceResponse> buildEvidence(Map<SourceMappingTarget, FieldMatch> matches) {
		List<MappingEvidenceResponse> evidence = new ArrayList<>();

		for (SourceMappingTarget target : SourceMappingTarget.values()) {
			FieldMatch match = matches.get(target);

			if (match == null) {
				continue;
			}

			evidence.add(new MappingEvidenceResponse(
							target.responseFieldName(),
							match.sourceField(),
							match.confidence(),
							match.reason()
					)
			);
		}

		return evidence;
	}

	private String sourceField(Map<SourceMappingTarget, FieldMatch> matches, SourceMappingTarget target) {
		FieldMatch match = matches.get(target);

		return match == null
				? null
				: match.sourceField();
	}
}