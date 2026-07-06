package com.pictoglyph.pictoglyphapi.repositories.core;

import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

	boolean existsByLanguageIdAndSymbolCodeIgnoreCase(Long languageId, String symbolCode);

	@Override
	@EntityGraph(attributePaths = "language")
	Optional<Symbol> findById(Long id);
}
