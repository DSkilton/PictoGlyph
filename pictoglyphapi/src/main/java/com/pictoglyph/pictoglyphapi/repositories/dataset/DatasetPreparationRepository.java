package com.pictoglyph.pictoglyphapi.repositories.dataset;

import com.pictoglyph.pictoglyphapi.entities.dataset.DatasetPreparation;
import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetPreparationRepository extends JpaRepository<DatasetPreparation, Long> {

	List<DatasetPreparation> findAllByStatusOrderByCreatedAtDesc(DatasetReadinessStatus status);
}
