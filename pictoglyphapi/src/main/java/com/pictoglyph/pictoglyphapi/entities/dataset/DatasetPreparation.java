package com.pictoglyph.pictoglyphapi.entities.dataset;

import com.pictoglyph.pictoglyphapi.entities.enums.DatasetReadinessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "dataset_preparation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DatasetPreparation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	@Builder.Default
	private DatasetReadinessStatus status = DatasetReadinessStatus.INGESTING;

	@Column(name = "status_reason", columnDefinition = "text")
	private String statusReason;

	@Column(name = "ingestion_completed_at")
	private LocalDateTime ingestionCompletedAt;

	@Column(name = "validated_at")
	private LocalDateTime validatedAt;

	@Column(name = "ready_for_ml_at")
	private LocalDateTime readyForMlAt;

	@Column(name = " excluded_at")
	private LocalDateTime excludedAt;

	@Column(name = " exclusion_reason", columnDefinition = "text")
	private String exclusionReason;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	@PrePersist
	public void onCreate() {
		LocalDateTime now = LocalDateTime.now();

		if (status == null) {
			status = DatasetReadinessStatus.INGESTING;
		}

		if (createdAt == null) {
			createdAt = now;
		}

		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void markIngestionComplete() {
		ensureNotExcluded();

		if (ingestionCompletedAt == null) {
			ingestionCompletedAt = LocalDateTime.now();
		}
	}

	private void ensureNotExcluded() {
		if (status == DatasetReadinessStatus.EXCLUDED) {
			throw new IllegalStateException("Excluded datasets cannot change readiness state");
		}
	}

	public void markValidating() {
		ensureNotExcluded();

		status = DatasetReadinessStatus.VALIDATING;
		statusReason = "Dataset validation is in progress";
		validatedAt = LocalDateTime.now();
		readyForMlAt = null;
	}

	public void markReviewRequired(String reason) {
		ensureNotExcluded();

		status = DatasetReadinessStatus.REVIEW_REQUIRED;
		statusReason = reason;
		readyForMlAt = null;
	}

	public void markRetryRequired(String reason) {
		ensureNotExcluded();

		status = DatasetReadinessStatus.RETRY_REQUIRED;
		statusReason = reason;
		readyForMlAt = null;
	}

	public void markReadyForMl() {
		ensureNotExcluded();

		status = DatasetReadinessStatus.READY_FOR_ML;
		statusReason = "Dataset validation passed";
		readyForMlAt = LocalDateTime.now();
	}

	public void exclude(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Dataset exclusion reason is required");
		}

		status = DatasetReadinessStatus.EXCLUDED;
		statusReason = "Dataset has been excluded";
		exclusionReason = reason.trim();
		excludedAt = LocalDateTime.now();
		readyForMlAt = null;
	}
}
