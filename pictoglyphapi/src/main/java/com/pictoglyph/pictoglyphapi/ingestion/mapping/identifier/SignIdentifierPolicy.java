
package com.pictoglyph.pictoglyphapi.ingestion.mapping.identifier;

import java.util.Locale;
import java.util.Set;

public final class SignIdentifierPolicy {

	private static final Set<String> SIGN_FIELDS = Set.of(
			"symbolcode", "symbolid", "symbolidentifier", "symbolref", "signcode", "signid", "signidentifier", "signref",
			"glyphcode", "glyphid", "glyphidentifier", "glyphref", "hieroglyphcode", "hieroglyphid", "hieroglyphref", "gardinercode",
			"gardinerid", "gardinerref"
	);

	private static final Set<String> ARTEFACT_FIELDS = Set.of(
			"objectnumber", "objectid", "objectidentifier", "objectref", "accessionnumber", "accessionid", "accessionidentifier",
			"inventorynumber", "inventoryid", "cataloguenumber", "catalogueid", "artifactid", "artifactnumber", "artefactid",
			"artefactnumber", "museumid", "recordid", "recordnumber", "collectionid"
	);

	private static final Set<String> GENERIC_FIELDS = Set.of("id", "identifier", "code", "number", "ref", "reference");

	private static final Set<String> ARTEFACT_CONTAINERS = Set.of(
			"object", "objects", "artefact", "artefacts", "artifact", "artifacts", "accession", "inventory", "catalogue", "record", "records"
	);

	private SignIdentifierPolicy() { }

	public static SourceIdentifierAssessment assess(String fieldPath) {

		if (fieldPath == null || fieldPath.isBlank()) {
			return unresolved("No identifier field was supplied");
		}

		String[] segments = fieldPath.trim().split("\\.");
		String leaf = normalise(segments[segments.length - 1]);

		if (ARTEFACT_FIELDS.contains(leaf)) {
			return new SourceIdentifierAssessment(
					SourceIdentifierRole.ARTEFACT_OR_RECORD,
					"Field name indicates an artefact, accession or collection record"
			);
		}

		if (SIGN_FIELDS.contains(leaf)) {
			return new SourceIdentifierAssessment(
					SourceIdentifierRole.SIGN_CANDIDATE,
					"Field name explicitly refers to a sign or glyph identifier"
			);
		}

		if (GENERIC_FIELDS.contains(leaf) && segments.length > 1) {
			String parent = normalise(segments[segments.length - 2]);

			if (ARTEFACT_CONTAINERS.contains(parent)) {
				return new SourceIdentifierAssessment(
						SourceIdentifierRole.ARTEFACT_OR_RECORD,
						"Identifier belongs to an artefact or collection-record object"
				);
			}
		}

		return unresolved("Field name does not establish that the value identifies a sign");
	}

	private static SourceIdentifierAssessment unresolved(String reason) {
		return new SourceIdentifierAssessment(
				SourceIdentifierRole.UNRESOLVED,
				reason
		);
	}

	private static String normalise(String fieldName) {
		return fieldName.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]", "");
	}
}
