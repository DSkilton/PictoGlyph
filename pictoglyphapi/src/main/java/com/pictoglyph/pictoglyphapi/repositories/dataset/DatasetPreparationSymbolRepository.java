package com.pictoglyph.pictoglyphapi.repositories.dataset;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparationSymbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetPreparationSymbolRepository extends JpaRepository<DatasetPreparationSymbol, Long> {

	boolean existsByDatasetPreparationIdAndSymbolId(Long datasetPreparationId, Long symbolId);

	List<DatasetPreparationSymbol> findAllByDatasetPreparationIdOrderBySymbolIdAsc(Long datasetPreparationId);

	long countByDatasetPreparationId(Long datasetPreparationId);
}
