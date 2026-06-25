package com.pictoglyph.pictoglyphapi.repositories.core;

import com.pictoglyph.pictoglyphapi.entities.core.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
