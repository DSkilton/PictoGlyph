package com.pictoglyph.pictoglyphapi.ingestion;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ImageChecksumService {

	public static final String CHECKSUM_ALGORITHM = "SHA-256";

	public String calculateSha256(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			throw new IllegalArgumentException("Image file path is required");
		}

		Path path = Path.of(filePath.trim());

		if (!Files.isReadable(path)) {
			throw new IllegalArgumentException("Image file was not found: " + path);
		}

		try (InputStream inputStream = Files.newInputStream(path)) {
			MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);

			byte[] buffer = new byte[8192];
			int bytesRead;

			while ((bytesRead = inputStream.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}

			return HexFormat.of().formatHex(digest.digest());

		} catch (IOException e) {
			throw new IllegalStateException("Could not calculate checksum for image: " + path, e);

		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(CHECKSUM_ALGORITHM + " checksum support is unavailable", e);
		}
	}
}
