package com.pictoglyph.pictoglyphapi.ingestion;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
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

	@Mock
	private SymbolRepository symbolRepository;

	private ImportedSymbolPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new ImportedSymbolPersistenceService(symbolRepository);
	}

	@Test
	void shouldSaveImportedSymbol() {
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

		Symbol result = service.saveImportedSymbol(unsavedSymbol);
		assertThat(result).isSameAs(savedSymbol);

		verify(symbolRepository).save(unsavedSymbol);
	}

	@Test
	void shouldRejectMissingSymbol() {
		assertThatThrownBy(() ->
				service.saveImportedSymbol(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Symbol is required");

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
				service.saveImportedSymbol(unsavedSymbol))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("saved without an id");

		verify(symbolRepository).save(unsavedSymbol);
	}
}