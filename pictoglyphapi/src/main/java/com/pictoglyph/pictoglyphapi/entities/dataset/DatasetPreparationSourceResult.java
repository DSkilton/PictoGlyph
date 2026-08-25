package com.pictoglyph.pictoglyphapi.entities.DatasetPreparation;

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
import jakarta.persistence.PrePersist;
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
		name = "dataset_preparation_source_result",
		indexes = {
				@Index(
						name = "idx_dataset_source_result_dataset",
						columnList = "dataset_preparation_id"
				),
				@Index(
						name = "idx_dataset_source_result_job",
						columnList = "ingestion_job_id"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_dataset_source_ingestion_job",
						columnNames = {
								"dataset_preparation_id",
								"ingestion_job_id"
						}
				)
		}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "datasetPreparation")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DatasetPreparationSourceResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "dataset_preparation_id",
			nullable = false
	)
	private DatasetPreparation datasetPreparation;

	@Column(name = "ingestion_job_id")
	private Long ingestionJobId;

	@Column(name = "source_type", nullable = false, length = 100)
	private String sourceType;

	@Column(name = "source_name", nullable = false)
	private String sourceName;

	@Column(name = "source_path", columnDefinition = "text")
	private String sourcePath;

	@Enumerated(EnumType.STRING)
	@Column(name = "ingestion_status", nullable = false, length = 100)
	private IngestionStatus ingestionStatus;

	@Column(name = "imported_count", nullable = false)
	private int importedCount;

	@Column(name = "skipped_count", nullable = false)
	private int skippedCount;

	@Column(name = "manual_processing_count", nullable = false)
	private int manualProcessingCount;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "recorded_at", nullable = false)
	private LocalDateTime recordedAt;

	@PrePersist
	void onCreate() {
		if (recordedAt == null) {
			recordedAt = LocalDateTime.now();
		}
	}
}
