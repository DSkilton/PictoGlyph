package com.pictoglyph.pictoglyphapi.repositories.core;

import com.pictoglyph.pictoglyphapi.entities.core.Language;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguageRepository extends JpaRepository<Language, Long> {

	boolean existsByNameIgnoreCase(String name);

	Optional<Language> findByNameIgnoreCase(String name);
}
