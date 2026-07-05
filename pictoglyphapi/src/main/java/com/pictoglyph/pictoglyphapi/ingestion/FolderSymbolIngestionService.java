package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionResultResponse;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FolderSymbolIngestionService {

	private static final String SOURCE_TYPE = "FOLDER";
	private static final String PNG = ".png";
	private final String JPG = ".jpg";
	private final String JPEG = ".jpeg";
	private final String WEBP = ".webp";
	private final String SVG = ".svg";

	private final LanguageRepository languageRepository;
	private final SymbolRepository symbolRepository;

	@Transactional
	public IngestionResultResponse ingestFolder(Long languageId, String folderPath) {
		Language language = languageRepository.findById(languageId)
				.orElseThrow(() -> new IllegalArgumentException("No language found for Id; " + languageId));

		Path sourceFolder = Path.of(folderPath.trim())
				.toAbsolutePath()
				.normalize();

		if (!Files.exists(sourceFolder)) {
			throw new IllegalArgumentException(
					"Folder does not exist: " + sourceFolder + " from input: " + folderPath
			);
		}

		if (!Files.isDirectory(sourceFolder)) {
			throw new IllegalArgumentException(
					"Path is not a folder: " + sourceFolder + " from input: " + folderPath
			);
		}

		List<Long> createdSymbolIds = new ArrayList<>();
		int skippedCount = 0;

		try (Stream<Path> files = Files.list(sourceFolder)) {
			for (Path file: files.filter(this::isSupportedImage).toList()) {
				String symbolCode = buildSymbolCode(file);

				if (symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(languageId, symbolCode)) {
					skippedCount++;
					continue;
				}

				Symbol symbol = Symbol.builder()
						.language(language)
						.symbolCode(symbolCode)
						.imagePath(file.toAbsolutePath().toString())
						.build();

				Symbol savedSymbol = symbolRepository.save(symbol);
				createdSymbolIds.add(savedSymbol.getId());
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not read folder: " + sourceFolder);
		}

		return new IngestionResultResponse(
				SOURCE_TYPE,
				sourceFolder.toAbsolutePath().toString(),
				createdSymbolIds.size(),
				skippedCount,
				createdSymbolIds
		);
	}

	private boolean isSupportedImage(Path path) {
		if (!Files.isRegularFile(path)) {
			return false;
		}

		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);

		return filename.endsWith(PNG)
				|| filename.endsWith(JPG)
				|| filename.endsWith(JPEG)
				|| filename.endsWith(WEBP)
				|| filename.endsWith(SVG);

	}

	private String buildSymbolCode(Path path) {
		String filename = path.getFileName().toString();
		int extensionIndex = filename.lastIndexOf(".");

		String filenameWithoutExtension = extensionIndex > 0
				? filename.substring(0, extensionIndex)
				: filename;

		return filenameWithoutExtension
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9_-]", "_")
				.replaceAll("_+", "_");
	}
}
