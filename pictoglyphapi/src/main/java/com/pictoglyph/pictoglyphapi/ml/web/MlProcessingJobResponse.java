package com.pictoglyph.pictoglyphapi.ml.web;

import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingStatus;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;

import java.time.LocalDateTime;

public record MlProcessingJobResponse(
		Long id,
		Long symbolId,
		MlProcessingTaskType taskType,
		String modelProfile,
		MlProcessingStatus status,
		int attemptCount,
		String inputChecksum,
		String lastError,
		LocalDateTime requestedAt,
		LocalDateTime startedAt,
		LocalDateTime completedAt
) {

	public static MlProcessingJobResponse from(MlProcessingJob job) {
		return new MlProcessingJobResponse(
				job.getId(),
				job.getSymbolId(),
				job.getTaskType(),
				job.getModelProfile(),
				job.getStatus(),
				job.getAttemptCount(),
				job.getInputChecksum(),
				job.getLastError(),
				job.getRequestedAt(),
				job.getStartedAt(),
				job.getCompletedAt()
		);
	}
}