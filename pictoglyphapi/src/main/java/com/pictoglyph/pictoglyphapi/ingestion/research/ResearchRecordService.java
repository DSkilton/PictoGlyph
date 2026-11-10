package com.pictoglyph.pictoglyphapi.ingestion.research;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.pictoglyph.pictoglyphapi.utils.Constants.PICTOGLYPH_PROVENANCE;

@Service
@RequiredArgsConstructor
public class ResearchRecordService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResearchRecordService.class);
	private static final Logger PROVENANCE_LOGGER = LoggerFactory.getLogger(PICTOGLYPH_PROVENANCE);

	private static final int MAX_ASSETS_PER_RECORD = 5000;

	private final ResearchSourceRecordRepository recordRepository;
	private final ResearchImageAssetRepository imageRepository;

	@Transactional
	public ResearchRecordResponse importRecord(ResearchRecordRequest request) {
		long startedNanos = System.nanoTime();

		try {
			validate(request);
		} catch (IllegalArgumentException exception) {
			LOGGER.warn("event=research_record_rejected reason={}", exception.getMessage());
			throw exception;
		}

		LOGGER.debug("event=research_import_started sourceId={} recordKey={} recordType={} submittedAssets={}", request.sourceId(), request.recordKey(), request.recordType(), request.images().size());

		ResearchSourceRecord record = recordRepository
				.findBySourceIdAndRecordKey(request.sourceId(), request.recordKey())
				.orElse(null);

		boolean created = record == null;

		if (created) {
			record = new ResearchSourceRecord();
			record.setSourceId(request.sourceId());
			record.setRecordKey(request.recordKey());
			record.setRecordType(request.recordType());
			record.setSourceUrl(request.sourceUrl());
			record.setArchaeologicalContext(request.archaeologicalContext().deepCopy());
			record.setRawRecord(request.rawRecord().deepCopy());
			record.setProvenance(request.provenance().deepCopy());
		} else if (!record.getRecordType().equals(request.recordType())
				|| !record.getSourceUrl().equals(request.sourceUrl())
				|| !record.getArchaeologicalContext().equals(request.archaeologicalContext())
				|| !record.getRawRecord().equals(request.rawRecord())) {

			LOGGER.warn("event=research_record_conflict sourceId={} recordKey={} reason=SOURCE_RECORD_CHANGED", request.sourceId(), request.recordKey());
			throw new ResearchEvidenceConflictException("Source record changed: version or review required before replacing original evidence");
		}

		record.setLastSeenAt(Instant.now());
		record = recordRepository.save(record);

		int assetCreated = 0;
		int assetUpdated = 0;
		for (ResearchImageAssetRequest image : request.images()) {
			ResearchImageAsset asset = imageRepository.findByResearchSourceRecord_IdAndAssetKey(record.getId(), image.assetKey())
					.orElse(null);

			if (asset == null) {
				asset = new ResearchImageAsset();
				asset.setResearchSourceRecord(record);
				asset.setAssetKey(image.assetKey());
				asset.setSourcePath(image.sourcePath());
				asset.setImageKind(image.imageKind());
				asset.setRights(image.rights());
				asset.setChecksumSha256(image.checksumSha256());
				asset.setAnalysis(image.analysis().deepCopy());
				imageRepository.save(asset);
				assetCreated++;

				LOGGER.debug("event=research_asset_created sourceId={} recordKey={} assetKey={} imageKind={}",
						request.sourceId(), request.recordKey(), image.assetKey(), image.imageKind());
			} else {
				if (!Objects.equals(asset.getSourcePath(), image.sourcePath())
						|| !Objects.equals(asset.getImageKind(), image.imageKind())
						|| !Objects.equals(asset.getChecksumSha256(), image.checksumSha256())
						|| !Objects.equals(asset.getRights(), image.rights())) {

					LOGGER.warn("event=research_asset_conflict sourceId={} recordKey={} assetKey={} reason=ASSET_IDENTITY_CHANGED",
							request.sourceId(), request.recordKey(), image.assetKey());


					throw new ResearchEvidenceConflictException("Image asset identity changed: review required for " + image.assetKey());
				}
				if (!asset.getAnalysis().equals(image.analysis())) {
					asset.setAnalysis(image.analysis().deepCopy());

					LOGGER.debug("event=research_asset_analysis_updated sourceId={} recordKey={} assetKey={}",
							request.sourceId(), request.recordKey(), image.assetKey());

					imageRepository.save(asset);
					assetUpdated++;
				}
			}
		}


		ResearchRecordResponse response =
				new ResearchRecordResponse(record.getId(), created, assetCreated, assetUpdated);

		logCommittedImport(request, response, startedNanos);

		return response;
	}


	private void logCommittedImport(
			ResearchRecordRequest request,
			ResearchRecordResponse response,
			long startedNanos
	) {
		if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()) {
			LOGGER.debug("event=research_import_commit_log_skipped sourceId={} recordKey={} reason=NO_ACTIVE_TRANSACTION",
					request.sourceId(), request.recordKey());
			return;
		}

		String sourceId = request.sourceId();
		String recordKey = request.recordKey();
		String recordType = request.recordType();

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

				LOGGER.info("event=research_record_committed sourceId={} recordKey={} recordType={} recordId={} created={} assetsCreated={} assetsUpdated={} elapsedMs={}",
						sourceId, recordKey, recordType, response.recordId(), response.created(), response.imageAssetsCreated(), response.imageAssetsUpdated(), elapsedMs);

				PROVENANCE_LOGGER.info("event=research_record_committed sourceId={} recordKey={} recordType={} recordId={} created={} assetsCreated={} assetsUpdated={} transactionStatus=COMMITTED contentStatus=UNVERIFIED",
						sourceId, recordKey, recordType, response.recordId(), response.created(), response.imageAssetsCreated(), response.imageAssetsUpdated());
			}
		});
	}

	private void validate(ResearchRecordRequest request) {
		if (request == null || request.images() == null || request.archaeologicalContext() == null || !request.archaeologicalContext().isObject() || request.rawRecord() == null || !request.rawRecord().isObject() || request.provenance() == null || !request.provenance().isObject()) {
			throw new IllegalArgumentException("Record, image list and JSON metadata objects are required");
		}

		if (isBlank(request.sourceId()) || request.sourceId().length() > 100 || isBlank(request.recordKey()) || request.recordKey().length() > 255 || isBlank(request.recordType()) || request.recordType().length() > 60 || isBlank(request.sourceUrl())) {
			throw new IllegalArgumentException("A valid source identity, record key, type and URL are required");
		}

		if (request.images().size() > MAX_ASSETS_PER_RECORD) {
			throw new IllegalArgumentException("Too many assets for one source record");
		}

		Set<String> seen = new HashSet<>();

		for (ResearchImageAssetRequest image : request.images()) {
			if (image == null || isBlank(image.assetKey()) || image.assetKey().length() > 100 || isBlank(image.sourcePath()) || isBlank(image.imageKind()) || image.analysis() == null || !image.analysis().isObject()) {
				throw new IllegalArgumentException("Image asset identity and analysis are required");
			}

			if (image.checksumSha256() != null && !image.checksumSha256().matches("(?i)[a-f0-9]{64}")) {
				throw new IllegalArgumentException("Image checksum must be SHA-256 hex");
			}

			if (!seen.add(image.assetKey())) {
				throw new IllegalArgumentException("Duplicate asset key in source record: " + image.assetKey());
			}
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
