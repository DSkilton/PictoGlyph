package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.api.MlApiContractVersions;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResponse;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingResultStatus;
import com.pictoglyph.pictoglyphapi.ml.client.MlProcessingClient;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.pictoglyph.pictoglyphapi.TestConstants.PICTOGLYPH_TEST_FILE_LOCATION;
import static com.pictoglyph.pictoglyphapi.TestConstants.SIGLIP_BASELINE_V1;
import static com.pictoglyph.pictoglyphapi.TestConstants.ABC_123_CHECKSUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MlProcessingJobProcessorTest {

	@Mock
	private MlProcessingJobRepository jobRepository;

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private MlProcessingRequestFactory requestFactory;

	@Mock
	private MlProcessingClient processingClient;

	@Mock
	private MlEmbeddingPersistenceService embeddingPersistenceService;

	private MlProcessingJobProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new MlProcessingJobProcessor(
				jobRepository,
				symbolRepository,
				requestFactory,
				processingClient,
				embeddingPersistenceService
		);
	}

	@Test
	void shouldProcessPendingJobSuccessfully() {
		MlProcessingJob job = createPendingJob();

		Symbol symbol = Symbol.builder()
				.id(42L)
				.symbolCode("A1")
				.imagePath(PICTOGLYPH_TEST_FILE_LOCATION)
				.build();

		MlProcessingRequest request = new MlProcessingRequest(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING,
				SIGLIP_BASELINE_V1,
				PICTOGLYPH_TEST_FILE_LOCATION,
				ABC_123_CHECKSUM,
				"A1",
				null,
				null
		);

		MlProcessingResponse response = new MlProcessingResponse(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingResultStatus.COMPLETED,
				List.of(),
				Instant.now(),
				null
		);

		when(jobRepository.findById(25L)).thenReturn(Optional.of(job));
		when(jobRepository.save(any(MlProcessingJob.class)))
				.thenAnswer(invocation ->
						invocation.getArgument(0)
				);
		when(symbolRepository.findById(42L)).thenReturn(Optional.of(symbol));
		when(requestFactory.create(job, symbol)).thenReturn(request);
		when(processingClient.process(request)).thenReturn(response);

		MlProcessingJob result = processor.processJob(25L);

		assertThat(result.getStatus()).isEqualTo(MlProcessingStatus.COMPLETED);
		assertThat(result.getAttemptCount()).isEqualTo(1);
		assertThat(result.getStartedAt()).isNotNull();
		assertThat(result.getCompletedAt()).isNotNull();

		verify(embeddingPersistenceService).persist(job, response);
		verify(jobRepository, times(2)).save(job);
	}

	@Test
	void shouldMarkJobFailedWhenPythonReportsFailure() {
		MlProcessingJob job = createPendingJob();

		Symbol symbol =
				Symbol.builder()
						.id(42L)
						.build();

		MlProcessingRequest request = mock(MlProcessingRequest.class);

		MlProcessingResponse response = new MlProcessingResponse(
				MlApiContractVersions.V1,
				25L,
				42L,
				MlProcessingResultStatus.FAILED,
				List.of(),
				Instant.now(),
				"Image could not be opened"
		);

		when(jobRepository.findById(25L)).thenReturn(Optional.of(job));
		when(jobRepository.save(any(MlProcessingJob.class)))
				.thenAnswer(invocation ->
						invocation.getArgument(0)
				);
		when(symbolRepository.findById(42L)).thenReturn(Optional.of(symbol));
		when(requestFactory.create(job, symbol)).thenReturn(request);
		when(processingClient.process(request)).thenReturn(response);

		MlProcessingJob result = processor.processJob(25L);

		assertThat(result.getStatus()).isEqualTo(MlProcessingStatus.FAILED);
		assertThat(result.getLastError())
				.isEqualTo(
						"Image could not be opened"
				);
		assertThat(result.getAttemptCount()).isEqualTo(1);

		verifyNoInteractions(embeddingPersistenceService);
	}

	@Test
	void shouldMarkJobFailedWhenMlClientThrowsException() {
		MlProcessingJob job = createPendingJob();

		Symbol symbol = Symbol.builder()
				.id(42L)
				.build();

		MlProcessingRequest request = mock(MlProcessingRequest.class);

		when(jobRepository.findById(25L)).thenReturn(Optional.of(job));

		when(jobRepository.save(any(MlProcessingJob.class)))
				.thenAnswer(invocation ->
						invocation.getArgument(0)
				);

		when(symbolRepository.findById(42L)).thenReturn(Optional.of(symbol));
		when(requestFactory.create(job, symbol)).thenReturn(request);
		when(processingClient.process(request)).thenThrow(new IllegalStateException("Could not call ML processing service"));

		MlProcessingJob result = processor.processJob(25L);

		assertThat(result.getStatus()).isEqualTo(MlProcessingStatus.FAILED);
		assertThat(result.getLastError()).contains("Could not call ML processing service");

		verifyNoInteractions(embeddingPersistenceService);
	}

	private MlProcessingJob createPendingJob() {
		return MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
				.modelProfile(SIGLIP_BASELINE_V1)
				.status(MlProcessingStatus.PENDING)
				.inputChecksum(ABC_123_CHECKSUM)
				.build();
	}
}
