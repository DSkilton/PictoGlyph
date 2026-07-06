package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionStatus;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionResultResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.ManualProcessingFileResponse;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FolderSymbolIngestionService {

	private static final String SOURCE_TYPE = "FOLDER";
	private static final String MANUAL_PROCESSING_FOLDER_NAME = "_manual_processing";

	private static final String PNG = ".png";
	private static final String JPG = ".jpg";
	private static final String JPEG = ".jpeg";
	private static final String WEBP = ".webp";
	private static final String SVG = ".svg";
	private static final String TIF = ".tif";
	private static final String TIFF = ".TIFF";
	public static final String FOLDER_INGESTION_FAILED_FOR = "Folder ingestion failed for: ";
	public static final String UNSUPPORTED_FILE_TYPE = "Unsupported file type";
	public static final String COULD_NOT_SAVE_SYMBOL = "Could not save symbol: ";

	private final LanguageRepository languageRepository;
	private final SymbolRepository symbolRepository;
	private final IngestionJobRepository ingestionJobRepository;

	public IngestionResultResponse ingestFolder(Long languageId, String folderPath) {
		Path sourceFolder = Path.of(folderPath.trim())
				.toAbsolutePath()
				.normalize();

		Path manualProcessingFolder = sourceFolder.resolve(MANUAL_PROCESSING_FOLDER_NAME);

		IngestionJob ingestionJob = createRunningJob(sourceFolder);

		try {
			Language language = languageRepository.findById(languageId)
					.orElseThrow(() -> new IllegalArgumentException("No language found for id: " + languageId));

			validateSourceFolder(sourceFolder);
			Files.createDirectories(manualProcessingFolder);

			FolderIngestionStats stats = processFolder(language, languageId, sourceFolder, manualProcessingFolder);

			IngestionStatus finalStatus = stats.manualProcessingFiles().isEmpty()
					? IngestionStatus.COMPLETED
					: IngestionStatus.COMPLETED_WITH_MANUAL_PROCESSING;

			completeJob(ingestionJob, finalStatus, stats.createdSymbolIds().size(), stats.skippedCount(), stats.manualProcessingFiles().size(), null);

			return buildResponse(
					ingestionJob,
					sourceFolder,
					manualProcessingFolder,
					stats.createdSymbolIds(),
					stats.skippedCount(),
					stats.manualProcessingFiles()
			);
		} catch (RuntimeException | IOException exception) {
			failJob(ingestionJob, exception);
			throw new IllegalStateException(FOLDER_INGESTION_FAILED_FOR + sourceFolder, exception);
		}
	}

	private FolderIngestionStats processFolder(Language language, Long languageId, Path sourceFolder, Path manualProcessingFolder) throws IOException {
		List<Long> createdSymbolIds = new ArrayList<>();
		List<ManualProcessingFileResponse> manualProcessingFiles = new ArrayList<>();
		int skippedCount = 0;

		try (Stream<Path> files = Files.list(sourceFolder)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				if (!isSupportedImage(file)) {
					manualProcessingFiles.add(
							moveToManualProcessing(file, manualProcessingFolder, UNSUPPORTED_FILE_TYPE)
					);
					continue;
				}

				String symbolCode = buildSymbolCode(file);

				if (symbolRepository.existsByLanguageIdAndSymbolCodeIgnoreCase(languageId, symbolCode)) {
					skippedCount++;
					continue;
				}

				try {
					Symbol symbol = Symbol.builder()
							.language(language)
							.symbolCode(symbolCode)
							.imagePath(file.toAbsolutePath().toString())
							.build();

					Symbol savedSymbol = symbolRepository.save(symbol);
					createdSymbolIds.add(savedSymbol.getId());
				} catch (RuntimeException exception) {
					manualProcessingFiles.add(
							moveToManualProcessing(file, manualProcessingFolder, COULD_NOT_SAVE_SYMBOL + exception.getMessage())
					);
				}
			}
		}

		return new FolderIngestionStats(
				createdSymbolIds,
				skippedCount,
				manualProcessingFiles
		);
	}

	private IngestionJob createRunningJob(Path sourceFolder) {
		IngestionJob ingestionJob = IngestionJob.builder()
				.sourceType(SOURCE_TYPE)
				.sourcePath(sourceFolder.toString())
				.status(IngestionStatus.RUNNING)
				.importedCount(0)
				.skippedCount(0)
				.manualProcessingCount(0)
				.build();

		return ingestionJobRepository.save(ingestionJob);
	}

	private void completeJob(IngestionJob ingestionJob, IngestionStatus status, int importedCount, int skippedCount, int manualProcessingCount, String errorMessage) {
		ingestionJob.setStatus(status);
		ingestionJob.setImportedCount(importedCount);
		ingestionJob.setSkippedCount(skippedCount);
		ingestionJob.setManualProcessingCount(manualProcessingCount);
		ingestionJob.setErrorMessage(errorMessage);
		ingestionJob.setCompletedAt(LocalDateTime.now());

		ingestionJobRepository.save(ingestionJob);
	}

	private void failJob(IngestionJob ingestionJob, Exception exception) {
		completeJob(ingestionJob, IngestionStatus.FAILED, ingestionJob.getImportedCount(), ingestionJob.getSkippedCount(), ingestionJob.getManualProcessingCount(), exception.getMessage());
	}

	private void validateSourceFolder(Path sourceFolder) {
		if (!Files.exists(sourceFolder)) {
			throw new IllegalArgumentException("Folder does not exist: " + sourceFolder);
		}

		if (!Files.isDirectory(sourceFolder)) {
			throw new IllegalArgumentException("Path is not a folder: " + sourceFolder);
		}
	}

	private ManualProcessingFileResponse moveToManualProcessing(Path sourceFile, Path manualProcessingFolder, String reason) {
		try {
			Files.createDirectories(manualProcessingFolder);

			Path targetFile = buildUniqueTargetPath(
					manualProcessingFolder.resolve(sourceFile.getFileName())
			);

			Files.move(sourceFile, targetFile);

			return new ManualProcessingFileResponse(
					sourceFile.toAbsolutePath().toString(),
					targetFile.toAbsolutePath().toString(),
					reason
			);
		} catch (IOException exception) {
			return new ManualProcessingFileResponse(
					sourceFile.toAbsolutePath().toString(),
					manualProcessingFolder.toAbsolutePath().toString(),
					"Could not move file for manual processing: " + exception.getMessage()
			);
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

	private boolean isSupportedImage(Path path) {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);

		return filename.endsWith(PNG)
				|| filename.endsWith(JPG)
				|| filename.endsWith(JPEG)
				|| filename.endsWith(WEBP)
				|| filename.endsWith(SVG)
				|| filename.endsWith(TIF)
				|| filename.endsWith(TIFF);
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

	private IngestionResultResponse buildResponse(
			IngestionJob ingestionJob,
			Path sourceFolder,
			Path manualProcessingFolder,
			List<Long> createdSymbolIds,
			int skippedCount,
			List<ManualProcessingFileResponse> manualProcessingFiles
	) {
		return new IngestionResultResponse(
				ingestionJob.getId(),
				SOURCE_TYPE,
				sourceFolder.toAbsolutePath().toString(),
				ingestionJob.getStatus(),
				createdSymbolIds.size(),
				skippedCount,
				manualProcessingFiles.size(),
				manualProcessingFolder.toAbsolutePath().toString(),
				createdSymbolIds,
				manualProcessingFiles
		);
	}
}