package com.pictoglyph.pictoglyphapi.repositories.core;

import com.pictoglyph.pictoglyphapi.entities.core.LanguagePlace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguagePlaceRepository extends JpaRepository<LanguagePlace, Long> {

	boolean existsByLanguage_IdAndPlace_Id(Long langaugeId, Long placeId);

	@EntityGraph(attributePaths = {"language", "place"})
	Optional<LanguagePlace> findByLanguage_IdAndPlace_Id(Long languageId, Long placeId);
}
