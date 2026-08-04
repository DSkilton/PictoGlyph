package com.pictoglyph.pictoglyphapi.repositories.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MlProcessingJobRepository extends JpaRepository<MlProcessingJob, Long> {
	List<MlProcessingJob> findAllByStatusOrderByRequestedAtAsc(MlProcessingStatus status);

	Optional<MlProcessingJob> findFirstByStatusOrderByRequestedAtAsc(MlProcessingStatus status);

	List<MlProcessingJob> findAllBySymbolIdOrderByRequestedAtDesc(Long symbolId);

	boolean existsBySymbolIdAndTaskTypeAndModelProfileAndStatusIn(Long symbolId, MlProcessingTaskType taskType, String modelProfile, Collection<MlProcessingStatus> statuses);

	Optional<MlProcessingJob> findFirstBySymbolIdAndTaskTypeAndModelProfileAndStatusInOrderByRequestedAtDesc(Long symbolId, MlProcessingTaskType taskType, String modelProfile, Collection<MlProcessingStatus> statuses);
}
