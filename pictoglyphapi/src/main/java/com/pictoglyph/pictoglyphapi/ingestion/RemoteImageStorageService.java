package com.pictoglyph.pictoglyphapi.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.pictoglyph.pictoglyphapi.utils.Constants.IMG;
import static com.pictoglyph.pictoglyphapi.utils.StringUtils.*;

@Service
@RequiredArgsConstructor
public class RemoteImageStorageService {

	@Value("${pictoglyph.ingestion.image-storage-root}")
	private String imageStorageRoot;

	private final RestTemplate restTemplate;

	private static final String DEFAULT_EXTENSION = IMG;

	public DownloadedImage downloadedImage(String imageUrl, String sourceType, Long languageId, String symbolCode) {
		validateImageUrl(imageUrl);

		byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);

		if (imageBytes == null || imageBytes.length ==0) {
			throw new IllegalStateException("Image download returned no data: " + imageUrl);
		}

		Path targetFolder = Path.of(imageStorageRoot)
				.toAbsolutePath()
				.normalize()
				.resolve(sourceType.toLowerCase(Locale.ROOT))
				.resolve("language-" + languageId);

		try {
			Files.createDirectories(targetFolder);

			String extension = extractExtension(imageUrl);
			String filename = cleanString(symbolCode) + extension;
			Path targetPath = buildUniqueTargetPath(targetFolder.resolve(filename));

			Files.write(targetPath, imageBytes);

			return new DownloadedImage(imageUrl, targetPath.toAbsolutePath().toString());

		} catch (IOException e) {
			throw new RuntimeException(e);
		}


	}

	private Path buildUniqueTargetPath(Path targetPath) {
		if (!Files.exists(targetPath)) {
			return targetPath;
		}

		String filename = targetPath.getFileName().toString();
		int extensionIndex = filename.lastIndexOf(".");

		String nameWithoutExtension = extensionIndex > 0
				? filename.substring(0, extensionIndex)
				: filename;

		String extension = extensionIndex > 0
				? filename.substring(extensionIndex)
				: "";

		Path parent = targetPath.getParent();
		int counter = 1;

		while (true) {
			Path candidate = parent.resolve(nameWithoutExtension + "_" + counter + extension);

			if (!Files.exists(candidate)) {
				return candidate;
			}

			counter++;
		}

	}

	private String extractExtension(String imageUrl) {
		String path = URI.create(imageUrl).getPath();

		if (path == null || path.isBlank()) {
			return DEFAULT_EXTENSION;
		}

		int extensionIndex = path.lastIndexOf(".");

		if (extensionIndex < 0) {
			return DEFAULT_EXTENSION;
		}

		String extension = path.substring(extensionIndex).toLowerCase(Locale.ROOT);

		if (extension.length() > 10) {
			return DEFAULT_EXTENSION;
		}

		return extension;
	}

	private void validateImageUrl(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			throw new IllegalArgumentException("Image URL cannot be blank");
		}

		URI uri = URI.create(imageUrl);

		if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
			throw new IllegalArgumentException("Image URL must start with http or https: " + imageUrl);
		}

	}
}
