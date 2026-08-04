package com.pictoglyph.pictoglyphapi.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageChecksumServiceTest {

	@TempDir
	Path temporaryDirectory;

	private ImageChecksumService service;

	@BeforeEach
	void setUp() {
		service = new ImageChecksumService();
	}

	@Test
	void shouldCalculateSha256Checksum() throws Exception {
		Path file = temporaryDirectory.resolve("sample-image.bin");

		Files.writeString(file, "abc", StandardCharsets.UTF_8);

		String checksum = service.calculateSha256(file.toString());

		assertThat(checksum).isEqualTo("ba7816bf8f01cfea414140de5dae2223" + "b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void shouldProduceSameChecksumForIdenticalFiles() throws Exception {
		Path firstFile = temporaryDirectory.resolve("first.bin");

		Path secondFile = temporaryDirectory.resolve("second.bin");

		byte[] content = {10, 20, 30, 40};

		Files.write(firstFile, content);
		Files.write(secondFile, content);

		String firstChecksum = service.calculateSha256(firstFile.toString());

		String secondChecksum = service.calculateSha256(secondFile.toString());

		assertThat(firstChecksum).isEqualTo(secondChecksum);
	}

	@Test
	void shouldProduceDifferentChecksumsForDifferentFiles() throws Exception {
		Path firstFile = temporaryDirectory.resolve("first.bin");
		Path secondFile = temporaryDirectory.resolve("second.bin");

		Files.writeString(firstFile, "image-one");
		Files.writeString(secondFile, "image-two");

		String firstChecksum = service.calculateSha256(firstFile.toString());
		String secondChecksum = service.calculateSha256(secondFile.toString());

		assertThat(firstChecksum).isNotEqualTo(secondChecksum);
	}

	@Test
	void shouldRejectMissingFilePath() {
		assertThatThrownBy(() ->
				service.calculateSha256(" ")
		)
				.isInstanceOf(
						IllegalArgumentException.class
				)
				.hasMessageContaining(
						"file path is required"
				);
	}

	@Test
	void shouldRejectFileThatDoesNotExist() {
		Path missingFile = temporaryDirectory.resolve("missing.png");

		assertThatThrownBy(() ->
				service.calculateSha256(
						missingFile.toString()
				)
		)
				.isInstanceOf(
						IllegalArgumentException.class
				)
				.hasMessageContaining(
						"Image file was not found"
				);
	}
}