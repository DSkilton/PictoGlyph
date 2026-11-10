package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchRecordServiceTest {

	@Mock
	private ResearchSourceRecordRepository recordRepository;

	@Mock
	private ResearchImageAssetRepository imageRepository;

	private ResearchRecordService service;
	private ObjectNode json;

	@BeforeEach
	void setUp() {
		service = new ResearchRecordService(recordRepository, imageRepository);
		json = new ObjectMapper().createObjectNode().put("period", "New Kingdom");
	}

	@Test
	void shouldReuseUnchangedRecordWithoutDuplicatingIt() {
		ResearchSourceRecord existing = existingRecord();

		when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123"))
				.thenReturn(Optional.of(existing));
		when(recordRepository.save(existing)).thenReturn(existing);

		ResearchRecordResponse response = service.importRecord(request(json));

		assertThat(response.created()).isFalse();
		assertThat(response.recordId()).isEqualTo(42L);
		assertThat(response.imageAssetsCreated()).isZero();
		assertThat(response.imageAssetsUpdated()).isZero();

		verify(recordRepository).save(existing);
		verifyNoInteractions(imageRepository);
	}


	@Test
	void shouldCreateImageAssetForNewResearchRecord() {
		ResearchImageAssetRequest image = imageRequest("image-1");
		ResearchRecordRequest request = requestWithImages(List.of(image));

		when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.empty());
		when(recordRepository.save(any(ResearchSourceRecord.class)))
				.thenAnswer(invocation -> {
					ResearchSourceRecord saved = invocation.getArgument(0);
					saved.setId(42L);
					return saved;
				});
		when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "image-1")).thenReturn(Optional.empty());

		ResearchRecordResponse response = service.importRecord(request);

		assertThat(response.created()).isTrue();
		assertThat(response.imageAssetsCreated()).isEqualTo(1);

		ArgumentCaptor<ResearchImageAsset> captor = ArgumentCaptor.forClass(ResearchImageAsset.class);

		verify(imageRepository).save(captor.capture());

		assertThat(captor.getValue().getAssetKey()).isEqualTo("image-1");
		assertThat(captor.getValue().getImageKind()).isEqualTo("ARTEFACT");
		assertThat(captor.getValue().getResearchSourceRecord().getId()).isEqualTo(42L);
	}

	@Test
	void shouldPersistArtefactWithoutSignCode() {
		ResearchRecordRequest request = request(json);

		when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.empty());
		when(recordRepository.save(any(ResearchSourceRecord.class))).thenAnswer(invocation -> {
			ResearchSourceRecord saved = invocation.getArgument(0);
			saved.setId(42L);
			return saved;
		});

		ResearchRecordResponse response = service.importRecord(request);

		assertThat(response.created()).isTrue();
		assertThat(response.recordId()).isEqualTo(42L);
		assertThat(response.imageAssetsCreated()).isZero();
		verifyNoInteractions(imageRepository);
	}

	@Test
	void shouldRejectChangedRawEvidence() {
		ResearchSourceRecord original = new ResearchSourceRecord();
		original.setId(42L);
		original.setRecordType("ARTEFACT");
		original.setSourceUrl("https://example.org/collection");
		original.setArchaeologicalContext(json.deepCopy());
		original.setRawRecord(json.deepCopy());
		when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123"))
				.thenReturn(Optional.of(original));

		ResearchRecordRequest changed = new ResearchRecordRequest(
				"egyptian", "object:EA123", "ARTEFACT", "https://example.org/collection",
				json.deepCopy(), new ObjectMapper().createObjectNode().put("different", true),
				json.deepCopy(), List.of()
		);

		assertThatThrownBy(() -> service.importRecord(changed))
				.isInstanceOf(ResearchEvidenceConflictException.class)
				.hasMessageContaining("Source record changed");
		verifyNoInteractions(imageRepository);
	}


	@Test
	void shouldRejectChangedImageIdentity() {
		ResearchSourceRecord existing = existingRecord();
		ResearchImageAsset asset = existingAsset(existing);
		asset.setChecksumSha256("b".repeat(64));

		when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123"))
				.thenReturn(Optional.of(existing));
		when(recordRepository.save(existing)).thenReturn(existing);
		when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "image-1"))
				.thenReturn(Optional.of(asset));

		assertThatThrownBy(() ->
				service.importRecord(requestWithImages(List.of(imageRequest("image-1")))))
				.isInstanceOf(ResearchEvidenceConflictException.class)
				.hasMessageContaining("Image asset identity changed");

		verify(imageRepository, never()).save(any(ResearchImageAsset.class));
	}

	@Test
	void shouldRejectDuplicateAssetKeysBeforePersistence() {
		ResearchImageAssetRequest image = imageRequest("image-1");

		assertThatThrownBy(() ->
				service.importRecord(requestWithImages(List.of(image, image))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Duplicate asset key");

		verifyNoInteractions(recordRepository, imageRepository);
	}

	@Test
	void shouldRejectInvalidImageChecksumBeforePersistence() {
		ResearchImageAssetRequest invalid = new ResearchImageAssetRequest(
				"image-1",
				"/images/ea123.jpg",
				"ARTEFACT",
				"CC BY",
				"invalid-checksum",
				json.deepCopy()
		);

		assertThatThrownBy(() ->
				service.importRecord(requestWithImages(List.of(invalid))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SHA-256");

		verifyNoInteractions(recordRepository, imageRepository);
	}


	private ResearchSourceRecord existingRecord() {
		ResearchSourceRecord record = new ResearchSourceRecord();
		record.setId(42L);
		record.setSourceId("egyptian");
		record.setRecordKey("object:EA123");
		record.setRecordType("ARTEFACT");
		record.setSourceUrl("https://example.org/collection");
		record.setArchaeologicalContext(json.deepCopy());
		record.setRawRecord(json.deepCopy());
		record.setProvenance(json.deepCopy());
		return record;
	}

	private ResearchImageAssetRequest imageRequest(String assetKey) {
		return new ResearchImageAssetRequest(
				assetKey,
				"/images/ea123.jpg",
				"ARTEFACT",
				"CC BY",
				"a".repeat(64),
				json.deepCopy()
		);
	}

	private ResearchRecordRequest requestWithImages(List<ResearchImageAssetRequest> images) {
		ResearchRecordRequest original = request(json);

		return new ResearchRecordRequest(
				original.sourceId(),
				original.recordKey(),
				original.recordType(),
				original.sourceUrl(),
				original.archaeologicalContext(),
				original.rawRecord(),
				original.provenance(),
				images
		);
	}

	private ResearchImageAsset existingAsset(ResearchSourceRecord record) {
		ResearchImageAsset asset = new ResearchImageAsset();
		asset.setId(100L);
		asset.setResearchSourceRecord(record);
		asset.setAssetKey("image-1");
		asset.setSourcePath("/images/ea123.jpg");
		asset.setImageKind("ARTEFACT");
		asset.setRights("CC BY");
		asset.setChecksumSha256("a".repeat(64));
		asset.setAnalysis(json.deepCopy());
		return asset;
	}

	private ResearchRecordRequest request(ObjectNode content) {
		return new ResearchRecordRequest(
				"egyptian", "object:EA123", "ARTEFACT", "https://example.org/collection",
				content.deepCopy(), content.deepCopy(), content.deepCopy(), List.of()
		);
	}
}
