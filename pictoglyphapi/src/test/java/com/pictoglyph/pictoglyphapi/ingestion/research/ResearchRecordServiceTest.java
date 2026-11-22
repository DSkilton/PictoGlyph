package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.ingestion.ImageChecksumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @TempDir
	Path imageDirectory;

    private ResearchRecordService service;
    private ObjectNode json;
    private ImageChecksumService imageChecksumService;

    @BeforeEach
    void setUp() throws IOException {
        imageChecksumService = new ImageChecksumService();
        service = new ResearchRecordService(recordRepository, imageRepository, imageChecksumService);
        ReflectionTestUtils.setField(service, "researchImageRoot", imageDirectory.toString());

        Files.writeString(imageDirectory.resolve("met-550876.jpg"), "test image contents");
        json = new ObjectMapper().createObjectNode().put("period", "New Kingdom");
    }

    @Test
    void shouldRejectCuratedImageWhenFileChecksumDiffers() throws IOException {
        ResearchSourceRecord existing = existingRecord();
        ResearchImageAssetRequest image = curatedImageRequest();

        Files.writeString(Path.of(image.sourcePath()), "changed image contents");

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
        when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, image.assetKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachImageAsset("egyptian", "object:EA123", image))
                .isInstanceOf(ResearchEvidenceConflictException.class)
                .hasMessageContaining("SHA-256 mismatch");

        verify(imageRepository, never()).save(any(ResearchImageAsset.class));
    }

    @Test
    void shouldReuseUnchangedRecordWithoutDuplicatingIt() {
        ResearchSourceRecord existing = existingRecord();

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
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
        when(recordRepository.save(any(ResearchSourceRecord.class))).thenAnswer(invocation -> {
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
        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(original));

        ResearchRecordRequest changed = new ResearchRecordRequest(
                "egyptian", "object:EA123", "ARTEFACT", "https://example.org/collection",
                json.deepCopy(), new ObjectMapper().createObjectNode().put("different", true), json.deepCopy(), List.of());

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

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
        when(recordRepository.save(existing)).thenReturn(existing);
        when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "image-1")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.importRecord(requestWithImages(List.of(imageRequest("image-1")))))
                .isInstanceOf(ResearchEvidenceConflictException.class)
                .hasMessageContaining("Image asset identity changed");

        verify(imageRepository, never()).save(any(ResearchImageAsset.class));
    }

    @Test
    void shouldRejectDuplicateAssetKeysBeforePersistence() {
        ResearchImageAssetRequest image = imageRequest("image-1");
        assertThatThrownBy(() -> service.importRecord(requestWithImages(List.of(image, image))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate asset key");

        verifyNoInteractions(recordRepository, imageRepository);
    }

    @Test
    void shouldRejectInvalidImageChecksumBeforePersistence() {
        ResearchImageAssetRequest invalid = new ResearchImageAssetRequest(
                "image-1", "/images/ea123.jpg", "ARTEFACT", "CC BY", "invalid-checksum", json.deepCopy());

        assertThatThrownBy(() -> service.importRecord(requestWithImages(List.of(invalid))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");

        verifyNoInteractions(recordRepository, imageRepository);
    }

    @Test
    void shouldAttachCuratedImageWithoutChangingMuseumRecord() {
        ResearchSourceRecord existing = existingRecord();
        ResearchImageAssetRequest image = curatedImageRequest();

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
        when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "commons:550876")).thenReturn(Optional.empty());

        ResearchRecordResponse response = service.attachImageAsset("egyptian", "object:EA123", image);

        assertThat(response.created()).isFalse();
        assertThat(response.recordId()).isEqualTo(42L);
        assertThat(response.imageAssetsCreated()).isEqualTo(1);
        assertThat(response.imageAssetsUpdated()).isZero();
        verify(recordRepository, never()).save(any(ResearchSourceRecord.class));

        ArgumentCaptor<ResearchImageAsset> captor = ArgumentCaptor.forClass(ResearchImageAsset.class);
        verify(imageRepository).save(captor.capture());

        assertThat(captor.getValue().getResearchSourceRecord()).isSameAs(existing);
        assertThat(captor.getValue().getChecksumSha256()).isEqualTo(image.checksumSha256());
        assertThat(captor.getValue().getAnalysis().path("trainingDataApproved").asBoolean()).isFalse();
    }

    @Test
    void shouldReuseUnchangedCuratedImageWithoutSavingAnything() {
        ResearchSourceRecord existing = existingRecord();
        ResearchImageAssetRequest request = curatedImageRequest();
        ResearchImageAsset asset = new ResearchImageAsset();
        asset.setSourcePath(request.sourcePath());
        asset.setImageKind(request.imageKind());
        asset.setRights(request.rights());
        asset.setChecksumSha256(request.checksumSha256());
        asset.setAnalysis(request.analysis().deepCopy());

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
        when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "commons:550876")).thenReturn(Optional.of(asset));

        ResearchRecordResponse response = service.attachImageAsset("egyptian", "object:EA123", request);

        assertThat(response.imageAssetsCreated()).isZero();
        assertThat(response.imageAssetsUpdated()).isZero();
        verify(imageRepository, never()).save(any(ResearchImageAsset.class));
        verify(recordRepository, never()).save(any(ResearchSourceRecord.class));
    }

    @Test
    void shouldRejectChangedCuratedImageEvidence() {
        ResearchSourceRecord existing = existingRecord();
        ResearchImageAssetRequest request = curatedImageRequest();
        ResearchImageAsset asset = new ResearchImageAsset();
        asset.setSourcePath(request.sourcePath());
        asset.setImageKind(request.imageKind());
        asset.setRights(request.rights());
        asset.setChecksumSha256("d".repeat(64));
        asset.setAnalysis(request.analysis().deepCopy());

        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.of(existing));
        when(imageRepository.findByResearchSourceRecord_IdAndAssetKey(42L, "commons:550876")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.attachImageAsset("egyptian", "object:EA123", request))
                .isInstanceOf(ResearchEvidenceConflictException.class)
                .hasMessageContaining("review required");
        verify(imageRepository, never()).save(any(ResearchImageAsset.class));
    }

    @Test
    void shouldRejectCuratedImageWithoutChecksumOrExplicitMlGate() {
        ResearchImageAssetRequest image = curatedImageRequest();

        assertThatThrownBy(() -> service.attachImageAsset("egyptian", "object:EA123", new ResearchImageAssetRequest(
                image.assetKey(), image.sourcePath(), image.imageKind(), image.rights(), null, image.analysis())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");

        assertThatThrownBy(() -> service.attachImageAsset("egyptian", "object:EA123", new ResearchImageAssetRequest(
                image.assetKey(), image.sourcePath(), image.imageKind(), image.rights(), image.checksumSha256(), json.deepCopy())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ML approval");

        verifyNoInteractions(recordRepository, imageRepository);
    }

    @Test
    void shouldNotAttachImageToUnknownResearchRecord() {
        when(recordRepository.findBySourceIdAndRecordKey("egyptian", "object:EA123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachImageAsset("egyptian", "object:EA123", curatedImageRequest()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Research record not found");
        verifyNoInteractions(imageRepository);
    }

    private ResearchImageAssetRequest curatedImageRequest() {
        Path path = imageDirectory.resolve("met-550876.jpg");
        String checksum = imageChecksumService.calculateSha256(path.toString());

        return new ResearchImageAssetRequest(
                "commons:550876",
                path.toString(),
                "LOCAL_RESEARCH_IMAGE",
                "CC0-1.0",
                checksum,
                new ObjectMapper().createObjectNode()
                        .put("trainingDataApproved", false)
                        .put("distributionSource", "Wikimedia Commons")
        );
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
        return new ResearchImageAssetRequest(assetKey, "/images/ea123.jpg", "ARTEFACT", "CC BY", "a".repeat(64), json.deepCopy());
    }

    private ResearchRecordRequest requestWithImages(List<ResearchImageAssetRequest> images) {
        ResearchRecordRequest original = request(json);
        return new ResearchRecordRequest(original.sourceId(), original.recordKey(), original.recordType(), original.sourceUrl(),
                original.archaeologicalContext(), original.rawRecord(), original.provenance(), images);
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
        return new ResearchRecordRequest("egyptian", "object:EA123", "ARTEFACT", "https://example.org/collection",
                content.deepCopy(), content.deepCopy(), content.deepCopy(), List.of());
    }
}
