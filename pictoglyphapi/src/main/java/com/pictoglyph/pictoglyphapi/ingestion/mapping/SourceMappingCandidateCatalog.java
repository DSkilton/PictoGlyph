package com.pictoglyph.pictoglyphapi.ingestion.mapping;

import java.util.List;
import java.util.Map;

public final class SourceMappingCandidateCatalog {

	private SourceMappingCandidateCatalog() { 	}

	public static final List<String> ITEM_ARRAY_FIELDS = List.of("symbols",  "items", "results", "data", "records", "objects", "collection", "members", "entries");

	private static final Map<SourceMappingTarget, List<String>> FIELD_CANDIDATES = Map.of(
			SourceMappingTarget.SYMBOL_CODE,
				List.of("symbolCode", "symbol_code", "code", "signCode", "sign_code", "glyphCode", "glyph_code", "gardinerCode", "gardiner_code",
					"objectNumber", "object_number", "accessionNumber", "accession_number", "identifier", "id"
			),

			SourceMappingTarget.IMAGE_PATH,
				List.of("imageUrl", "image_url", "imagePath", "image_path", "image", "images", "thumbnailUrl", "thumbnail_url", "thumbnail", "mediaUrl",
					"media_url", "assetUrl", "asset_url", "url", "contentUrl", "content_url"
			),

			SourceMappingTarget.TITLE,
				List.of("label", "title", "name", "displayName", "display_name", "caption", "preferredLabel", "preferred_label"
			),

			SourceMappingTarget.DESCRIPTION,
				List.of("description", "summary", "notes", "note", "commentary", "details", "objectDescription", "object_description"
			),

			SourceMappingTarget.PLACE,
				List.of("place", "placeFound", "place_found", "findspot", "findSpot", "location", "region", "country", "provenance", "origin"
			),

			SourceMappingTarget.PERIOD,
				List.of("period", "era", "dynasty", "culture", "date", "dateRange", "date_range", "productionDate", "production_date"
			),

			SourceMappingTarget.DATE_START,
				List.of("dateStart", "date_start", "startDate", "start_date", "fromDate", "from_date", "yearStart", "year_start", "beginDate", "begin_date"
			),

			SourceMappingTarget.DATE_END,
				List.of("dateEnd", "date_end", "endDate", "end_date", "toDate", "to_date", "yearEnd", "year_end", "finishDate", "finish_date"
			)
	);

	public static List<String> candidatesFor(SourceMappingTarget target) {
		return FIELD_CANDIDATES.getOrDefault(target, List.of());
	}
}
