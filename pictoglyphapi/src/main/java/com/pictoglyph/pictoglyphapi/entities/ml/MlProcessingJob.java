package com.pictoglyph.pictoglyphapi.entities.ml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(
	name = "ml_processing_job",
	indexes = {
			@Index(
				name = "idx_ml_processing_job_status_requested",
				columnList = "status, requested_at"
			),
			@Index(
					name = "idx_ml_processing_job_symbol",
					columnList = "symbol_id"
			)

	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MlProcessingJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "symbol_id", nullable = false)
	private Long symbolId;

	@Enumerated(EnumType.STRING)
	@Column(name = "task_type", nullable = false, length = 50)
	private MlProcessingTaskType taskType;

	@Column(name = "model_profile", nullable = false, length = 150)
	private String modelProfile;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	@Builder.Default
	private MlProcessingStatus status = MlProcessingStatus.PENDING;

	@Column(name = "attempt_count", nullable = false)
	@Builder.Default
	private int attemptCount = 0;

	@Column(name = "input_checksum", length = 128)
	private String inputChecksum;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();

		if (requestedAt == null) {
			requestedAt = now;
		}

		if (updatedAt == null) {
			updatedAt = now;
		}

		if (status == null) {
			status = MlProcessingStatus.PENDING;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt =LocalDateTime.now();
	}

	public void markProcessing() {
		if (this.status != MlProcessingStatus.PENDING && this.status != MlProcessingStatus.FAILED) {
			throw new IllegalStateException("Only pending or faild ML jobs can begin processing");
		}

		this.status = MlProcessingStatus.PROCESSING;
		this.attemptCount++;
		this.startedAt = LocalDateTime.now();
		this.completedAt = null;
		this.lastError = null;
	}

	public void markCompleted() {
		requireProcessingStatus();

		this.status = MlProcessingStatus.COMPLETED;
		this.completedAt = LocalDateTime.now();
		this.lastError = null;
	}

	private void requireProcessingStatus(){
		if (this.status != MlProcessingStatus.PROCESSING) {
			throw new IllegalStateException("ML job must be processing before it can finish");
		}
	}

	public void markFailed(String errorMessage) {
		requireProcessingStatus();

		this.status = MlProcessingStatus.FAILED;
		this.completedAt = LocalDateTime.now();
		this.lastError = cleanErrorMessage(errorMessage);
	}

	private String cleanErrorMessage(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "Unknown ML processing error";
		}
		return errorMessage.trim();
	}

	public void resetForRetry() {
		if (this.status != MlProcessingStatus.FAILED) {
			throw new IllegalStateException("Only failed ML jobs can be reset for retry");
		}

		this.status = MlProcessingStatus.PENDING;
		this.startedAt = null;
		this.completedAt = null;
		this.lastError = null;
	}

	public void cancel() {
		if (this.status == MlProcessingStatus.COMPLETED) {
			throw new IllegalStateException("Completed ML jobs cannot be cancelled");
		}

		this.status = MlProcessingStatus.CANCELLED;
		this.completedAt = LocalDateTime.now();
	}



}
