package com.pictoglyph.pictoglyphapi.ingestion.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResearchImageAssetRepository extends JpaRepository<ResearchImageAsset, Long> {

	Optional<ResearchImageAsset> findByResearchSourceRecord_IdAndAssetKey(Long recordId, String assetKey);
}
