package com.pictoglyph.pictoglyphapi.repositories.core;

import com.pictoglyph.pictoglyphapi.entities.core.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	boolean existsByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);

	Optional<Place> findByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);
}
