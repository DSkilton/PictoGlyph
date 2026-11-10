
package com.pictoglyph.pictoglyphapi.ingestion.mapping.identifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignIdentifierPolicyTest {

	@Test
	void shouldRecogniseExplicitSignIdentifierFields() {
		for (String field : List.of("symbolCode", "sign_code", "glyph_ref", "gardinerCode", "results.hieroglyph_code")) {
			SourceIdentifierAssessment assessment = SignIdentifierPolicy.assess(field);
			assertThat(assessment.role())
					.as("Field: " + field)
					.isEqualTo(SourceIdentifierRole.SIGN_CANDIDATE);

			assertThat(assessment.eligibleForAutomaticMapping()).isTrue();
		}
	}

	@Test
	void shouldRecogniseArtefactAndRecordIdentifiers() {
		for (String field : List.of("object_number", "accessionNumber", "inventory_id", "object.id", "records.identifier")) {
			SourceIdentifierAssessment assessment = SignIdentifierPolicy.assess(field);

			assertThat(assessment.role())
					.as("Field: " + field)
					.isEqualTo(SourceIdentifierRole.ARTEFACT_OR_RECORD);

			assertThat(assessment.eligibleForAutomaticMapping()).isFalse();
		}
	}

	@Test
	void shouldLeaveGenericIdentifiersUnresolved() {
		for (String field : List.of("id", "code", "identifier", "reference")) {
			SourceIdentifierAssessment assessment = SignIdentifierPolicy.assess(field);

			assertThat(assessment.role())
					.as("Field: " + field)
					.isEqualTo(SourceIdentifierRole.UNRESOLVED);

			assertThat(assessment.reason()).isNotBlank();
		}
	}

	@Test
	void shouldNotAssumeMissingIdentifierIsValid() {
		assertThat(SignIdentifierPolicy.assess(null).eligibleForAutomaticMapping()).isFalse();
		assertThat(SignIdentifierPolicy.assess(" ").eligibleForAutomaticMapping()).isFalse();
	}
}
