package com.pictoglyph.pictoglyphapi.entities.ml;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MlProcessingJobRepo extends JpaRepository<MlProcessingJobRepo, Long> {
	List<MlProcessingJob> findAllByStatusOrderByRequestedAtAsc(MlProcessingStatus status);

	Optional<MlProcessingJob> findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus status);

	List<MlProcessingJob> findAllBySymbolIdOrderByRequestedAtDesc(Long symbolId);

	boolean existsBySymbolIdAndTaskTypeAndModelProfileAndStatusIn(Long symbolId, MlProcessingTaskType taskType, String modelProfile, Collection<MlProcessingStatus> statuses);
}
