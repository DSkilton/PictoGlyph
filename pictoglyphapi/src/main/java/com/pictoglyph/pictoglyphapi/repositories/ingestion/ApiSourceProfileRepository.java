
package com.pictoglyph.pictoglyphapi.repositories.ingestion;

import com.pictoglyph.pictoglyphapi.entities.enums.ApiSourceProfileStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.ApiSourceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiSourceProfileRepository extends JpaRepository<ApiSourceProfile, Long> {

	boolean existsByProfileNameIgnoreCase(String profileName);

	List<ApiSourceProfile> findAllByOrderByCreatedAtDesc();

	Optional<ApiSourceProfile> findFirstBySourceNameIgnoreCaseAndApiUrlAndStatusOrderByApprovedAtDescIdDesc(String sourceName, String apiUrl, ApiSourceProfileStatus status);
}
