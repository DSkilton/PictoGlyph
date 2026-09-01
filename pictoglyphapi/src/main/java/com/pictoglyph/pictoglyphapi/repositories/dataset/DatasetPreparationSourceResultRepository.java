package com.pictoglyph.pictoglyphapi.repositories.dataset;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSourceResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetPreparationSourceResultRepository extends JpaRepository<DatasetPreparationSourceResult, Long> {

	List<DatasetPreparationSourceResult> findAllByDatasetPreparationIdOrderByIdAsc(Long datasetPreparationId);

	Optional<DatasetPreparationSourceResult> findByDatasetPreparationIdAndIngestionJobId(Long datasetPreparationId, Long ingestionJobId);

	long countByDatasetPreparationId(Long datasetPreparationId);
}
