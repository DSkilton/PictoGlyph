package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.ml.MlProcessingJobQueueService;
import com.pictoglyph.pictoglyphapi.repositories.core.SymbolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportedSymbolPersistenceServiceTest {

	private static final String MODEL_PROFILE = "SIGLIP_BASELINE_V1";

	@Mock
	private SymbolRepository symbolRepository;

	@Mock
	private MlProcessingJobQueueService mlProcessingJobQueueService;

	private ImportedSymbolPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new ImportedSymbolPersistenceService(symbolRepository, mlProcessingJobQueueService);
	}

	@Test
	void shouldSaveSymbolAndQueueImageEmbedding() {
		Symbol unsavedSymbol = Symbol.builder()
				.symbolCode("A1")
				.imagePath("images/a1.png")
				.build();

		Symbol savedSymbol = Symbol.builder()
				.id(42L)
				.symbolCode("A1")
				.imagePath("images/a1.png")
				.build();

		when(symbolRepository.save(unsavedSymbol)).thenReturn(savedSymbol);

		Symbol result = service.saveAndQueueImageEmbedding(unsavedSymbol, MODEL_PROFILE, "abc123");
		assertThat(result).isSameAs(savedSymbol);

		verify(symbolRepository).save(unsavedSymbol);
		verify(mlProcessingJobQueueService).queueImageEmbedding(42L, MODEL_PROFILE, "abc123");
	}

	@Test
	void shouldRejectMissingSymbol() {
		assertThatThrownBy(() ->
				service.saveAndQueueImageEmbedding(
						null,
						MODEL_PROFILE,
						null
				)
		)
				.isInstanceOf(
						IllegalArgumentException.class
				)
				.hasMessageContaining(
						"Symbol is required"
				);

		verify(symbolRepository, never()).save(any());
	}

	@Test
	void shouldRejectSavedSymbolWithoutId() {
		Symbol unsavedSymbol = Symbol.builder()
				.symbolCode("A1")
				.build();

		Symbol savedWithoutId = Symbol.builder()
				.symbolCode("A1")
				.build();

		when(symbolRepository.save(unsavedSymbol)).thenReturn(savedWithoutId);

		assertThatThrownBy(() ->
				service.saveAndQueueImageEmbedding(
						unsavedSymbol,
						MODEL_PROFILE,
						null
				)
		)
				.isInstanceOf(
						IllegalStateException.class
				)
				.hasMessageContaining(
						"saved without an id"
				);

		verify(mlProcessingJobQueueService, never()).queueImageEmbedding(anyLong(), anyString(), any());
	}

	@Test
	void shouldPropagateQueueFailure() {
		Symbol unsavedSymbol = Symbol.builder()
				.symbolCode("A1")
				.build();

		Symbol savedSymbol = Symbol.builder()
				.id(42L)
				.symbolCode("A1")
				.build();

		when(symbolRepository.save(unsavedSymbol)).thenReturn(savedSymbol);

		doThrow(new IllegalStateException("Could not create ML job"))
				.when(mlProcessingJobQueueService)
				.queueImageEmbedding(
						42L,
						MODEL_PROFILE,
						null
				);

		assertThatThrownBy(() ->
				service.saveAndQueueImageEmbedding(
						unsavedSymbol,
						MODEL_PROFILE,
						null
				)
		)
				.isInstanceOf(
						IllegalStateException.class
				)
				.hasMessageContaining(
						"Could not create ML job"
				);
	}
}