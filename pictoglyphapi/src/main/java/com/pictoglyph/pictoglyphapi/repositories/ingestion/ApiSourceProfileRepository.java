package com.pictoglyph.pictoglyphapi.repositories.ingestion;

import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiSourceProfileRepository extends JpaRepository<ApiSourceProfile, Long> {

	boolean existsByProfileNameIgnoreCase(String profileName);
	List<ApiSourceProfile> findAllByOrderByCreatedAtDesc();

}
