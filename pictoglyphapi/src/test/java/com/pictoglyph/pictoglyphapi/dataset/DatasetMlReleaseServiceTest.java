package com.pictoglyph.pictoglyphapi.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.dataset.api.ReleaseDatasetToMlResponse;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSymbol;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.ingestion.ImageChecksumService;
import com.pictoglyph.pictoglyphapi.ml.MlJobQueueResult;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobQueueService;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationRepository;
import com.pictoglyph.pictoglyphapi.repositories.dataset.DatasetPreparationSymbolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetMlReleaseServiceTest {

	private static final Long DATASET_ID = 4L;
	private static final String MODEL_PROFILE = "SIGLIP_BASELINE_V1";
	private static final String CHECKSUM_ONE = "checksum-one";
	private static final String CHECKSUM_TWO = "checksum-two";

	@Mock
	private DatasetPreparationRepository datasetPreparationRepository;

	@Mock
	private DatasetPreparationSymbolRepository datasetPreparationSymbolRepository;

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private ImageChecksumService imageChecksumService;

	@Mock
	private MlProcessingJobQueueService mlProcessingJobQueueService;

	private DatasetMlReleaseService service;

	@BeforeEach
	void setUp() {
		service = new DatasetMlReleaseService(datasetPreparationRepository, datasetPreparationSymbolRepository, symbolRepository, imageChecksumService, mlProcessingJobQueueService);
	}

	@Test
	void shouldReleaseVerifiedDatasetSymbolsToMl() {
		DatasetPreparation preparation = readyDataset();

		DatasetPreparationSymbol datasetSymbolOne = datasetSymbol(1L);
		DatasetPreparationSymbol datasetSymbolTwo = datasetSymbol(2L);

		Symbol symbolOne = symbol(1L, "/images/one.jpg", CHECKSUM_ONE);
		Symbol symbolTwo = symbol(2L, "/images/two.jpg", CHECKSUM_TWO);

		when(datasetPreparationRepository.findById(DATASET_ID)).thenReturn(Optional.of(preparation));
		when(datasetPreparationSymbolRepository.findAllByDatasetPreparationIdOrderBySymbolIdAsc(DATASET_ID)).thenReturn(List.of(datasetSymbolOne, datasetSymbolTwo));

		when(symbolRepository.findById(1L)).thenReturn(Optional.of(symbolOne));
		when(symbolRepository.findById(2L)).thenReturn(Optional.of(symbolTwo));

		when(imageChecksumService.calculateSha256("/images/one.jpg")).thenReturn(CHECKSUM_ONE);
		when(imageChecksumService.calculateSha256("/images/two.jpg")).thenReturn(CHECKSUM_TWO);

		when(mlProcessingJobQueueService.queueImageEmbedding(1L, MODEL_PROFILE, CHECKSUM_ONE))
				.thenReturn(new MlJobQueueResult(mlJob(101L, 1L), true));

		when(mlProcessingJobQueueService.queueImageEmbedding(2L, MODEL_PROFILE, CHECKSUM_TWO))
				.thenReturn(new MlJobQueueResult(mlJob(102L, 2L), true));

		ReleaseDatasetToMlResponse response = service.release(DATASET_ID, MODEL_PROFILE);

		assertThat(response.datasetPreparationId()).isEqualTo(DATASET_ID);
		assertThat(response.modelProfile()).isEqualTo(MODEL_PROFILE);
		assertThat(response.symbolCount()).isEqualTo(2);
		assertThat(response.createdJobCount()).isEqualTo(2);
		assertThat(response.existingJobCount()).isZero();

		verify(mlProcessingJobQueueService).queueImageEmbedding(1L, MODEL_PROFILE, CHECKSUM_ONE);
		verify(mlProcessingJobQueueService).queueImageEmbedding(2L, MODEL_PROFILE, CHECKSUM_TWO);
	}

	@Test
	void shouldNotQueueAnyJobsWhenAnyChecksumDoesNotMatch() {
		DatasetPreparation preparation = readyDataset();

		DatasetPreparationSymbol datasetSymbolOne = datasetSymbol(1L);
		DatasetPreparationSymbol datasetSymbolTwo = datasetSymbol(2L);

		Symbol symbolOne = symbol(1L, "/images/one.jpg", CHECKSUM_ONE);
		Symbol symbolTwo = symbol(2L, "/images/two.jpg", CHECKSUM_TWO);

		when(datasetPreparationRepository.findById(DATASET_ID)).thenReturn(Optional.of(preparation));
		when(datasetPreparationSymbolRepository.findAllByDatasetPreparationIdOrderBySymbolIdAsc(DATASET_ID))
				.thenReturn(List.of(datasetSymbolOne, datasetSymbolTwo));

		when(symbolRepository.findById(1L)).thenReturn(Optional.of(symbolOne));
		when(symbolRepository.findById(2L)).thenReturn(Optional.of(symbolTwo));

		when(imageChecksumService.calculateSha256("/images/one.jpg")).thenReturn(CHECKSUM_ONE);
		when(imageChecksumService.calculateSha256("/images/two.jpg")).thenReturn("different-checksum");

		assertThatThrownBy(() -> service.release(DATASET_ID, MODEL_PROFILE))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Image checksum mismatch for symbol 2");

		verifyNoInteractions(mlProcessingJobQueueService);
	}

	@Test
	void shouldRejectDatasetThatIsNotReadyForMl() {
		DatasetPreparation preparation = DatasetPreparation.builder()
				.id(DATASET_ID)
				.name("Cleveland Egyptian Pilot - 25")
				.status(DatasetReadinessStatus.REVIEW_REQUIRED)
				.build();

		when(datasetPreparationRepository.findById(DATASET_ID)).thenReturn(Optional.of(preparation));

		assertThatThrownBy(() -> service.release(DATASET_ID, MODEL_PROFILE))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("READY_FOR_ML");

		verify(datasetPreparationSymbolRepository, never()).findAllByDatasetPreparationIdOrderBySymbolIdAsc(DATASET_ID);

		verifyNoInteractions(symbolRepository, imageChecksumService, mlProcessingJobQueueService);
	}

	private DatasetPreparation readyDataset() {
		return DatasetPreparation.builder()
				.id(DATASET_ID)
				.name("Cleveland Egyptian Pilot - 25")
				.status(DatasetReadinessStatus.READY_FOR_ML)
				.build();
	}

	private DatasetPreparationSymbol datasetSymbol(Long symbolId) {
		return DatasetPreparationSymbol.builder()
				.symbolId(symbolId)
				.build();
	}

	private Symbol symbol(Long id, String imagePath, String checksum) {
		ObjectNode metadata = new ObjectMapper().createObjectNode();

		metadata.put("imageChecksum", checksum);
		metadata.put("imageChecksumAlgorithm", ImageChecksumService.CHECKSUM_ALGORITHM);

		return Symbol.builder()
				.id(id)
				.imagePath(imagePath)
				.symbolCode("TEST_" + id)
				.meta(metadata)
				.build();
	}

	private MlProcessingJob mlJob(Long id, Long symbolId) {
		return MlProcessingJob.builder()
				.id(id)
				.symbolId(symbolId)
				.modelProfile(MODEL_PROFILE)
				.build();
	}
}