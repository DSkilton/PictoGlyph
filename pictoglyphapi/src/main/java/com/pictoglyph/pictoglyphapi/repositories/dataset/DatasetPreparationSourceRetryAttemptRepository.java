package com.pictoglyph.pictoglyphapi.repositories.dataset;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceRetryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetPreparationSourceRetryAttemptRepository extends JpaRepository<DatasetPreparationSourceRetryAttempt, Long> {

	long countBySourceResult_Id(Long sourceResultId);

	List<DatasetPreparationSourceRetryAttempt> findAllBySourceResult_IdOrderByAttemptNumberAsc(Long sourceResultId);
}
