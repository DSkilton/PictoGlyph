package com.pictoglyph.pictoglyphapi.ingestion;

public record DownloadedImage(
		String originalUrl,
		String localPath
) {
}
