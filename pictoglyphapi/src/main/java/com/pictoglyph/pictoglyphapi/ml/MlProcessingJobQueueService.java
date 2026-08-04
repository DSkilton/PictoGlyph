package com.pictoglyph.pictoglyphapi.ml;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.repositories.ml.MlProcessingJobRepository;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MlProcessingJobQueueService {

	private static final List<MlProcessingStatus> DUPLICATE_BLOCKING_STATUSES = List.of(MlProcessingStatus.CANCELLED, MlProcessingStatus.COMPLETED, MlProcessingStatus.FAILED, MlProcessingStatus.PENDING, MlProcessingStatus.PROCESSING);
	private final MlProcessingJobRepository mlProcessingJobRepository;

	@Transactional
	public MlJobQueueResult queue(Long symbolId, MlProcessingTaskType taskType, String modelProfile, String inputChecksum) {
		validateSymbolId(symbolId);

		if (taskType == null) {
			throw new IllegalArgumentException("ML processing task type is required");
		}

		String cleanModelProfile = requiredModelProfile(modelProfile);
		String cleanChecksum = cleanNullable(inputChecksum);
		Optional<MlProcessingJob> existingJob = mlProcessingJobRepository.findFirstBySymbolIdAndTaskTypeAndModelProfileAndStatusInOrderByRequestedAtDesc(symbolId, taskType, cleanModelProfile, DUPLICATE_BLOCKING_STATUSES);

		if (existingJob.isPresent()) {
			return new MlJobQueueResult(existingJob.get(), false);
		}

		MlProcessingJob newJob = MlProcessingJob.builder()
				.symbolId(symbolId)
				.taskType(taskType)
				.modelProfile(cleanModelProfile)
				.inputChecksum(cleanChecksum)
				.build();

		MlProcessingJob savedJob = mlProcessingJobRepository.save(newJob);

		return new MlJobQueueResult(savedJob, true);
	}

	public MlJobQueueResult queueImageEmbedding(Long symbolId, String modelProfile, String inputChecksum) {
		return queue(symbolId, MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING, modelProfile, inputChecksum);
	}

	private String requiredModelProfile(String modelProfile) {
		if (modelProfile == null || modelProfile.isBlank()) {
			throw new IllegalArgumentException("ML model profile is required");
		}

		return modelProfile.trim();
	}

	private String cleanNullable(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private void validateSymbolId(Long symbolId) {
		if (symbolId == null || symbolId <= 0) {
			throw new IllegalArgumentException("A valid symbol id is required");
		}
	}
}
