package com.pictoglyph.pictoglyphapi.repositories.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MlEmbeddingRepository extends JpaRepository<MlEmbedding, Long> {

	List<MlEmbedding> findAllBySymbolIdOrderByCreatedAtDesc(Long symbolId);

	List<MlEmbedding> findAllByProcessingJobId(Long processingJobId);

	Optional<MlEmbedding> findByProcessingJobIdAndModelNameAndModelVersion(Long processingJobId, String modelName, String modelVersion);

	boolean existsByProcessingJobIdAndModelNameAndModelVersion(Long processingJobId, String modelName, String modelVersion);
}
