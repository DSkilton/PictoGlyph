package com.pictoglyph.pictoglyphapi.entities.dataset;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
		name = "dataset_preparation_source_retry_attempt",
		indexes = {
				@Index(
						name = "idx_dataset_retry_source_result",
						columnList = "source_result_id"
				),
				@Index(
						name = "idx_dataset_retry_ingestion_job",
						columnList = "ingestion_job_id"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_dataset_retry_attempt",
						columnNames = {
								"source_result_id",
								"attempt_number"
						}
				)
		}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "sourceResult")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DatasetPreparationSourceRetryAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "source_result_id",
			nullable = false
	)
	private DatasetPreparationSourceResult sourceResult;

	@Column(
			name =  "attempt_number",
			nullable = false
	)
	private int attemptNumber;

	@Column(name = "ingestion_job_id")
	private Long ingestionJobId;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "ingestion_status",
			nullable = false,
			length = 100
	)
	private IngestionStatus ingestionStatus;

	@Column(name = "imported_count", nullable = false)
	private int importedCount;

	@Column(name = "skipped_count", nullable = false)
	private int skippedCount;

	@Column(name = "manual_processing_count", nullable = false)
	private int manualProcessingCount;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "completed_at", nullable = false)
	private LocalDateTime completedAt;

}
