package com.pictoglyph.pictoglyphapi.entities.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IngestionJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "source_type", nullable = false)
	private String sourceType;

	@Column(name = "source_path", nullable = false, length = 1000)
	private String sourcePath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IngestionStatus status;

	@Column(name = "imported_count", nullable = false)
	private int importedCount;

	@Column(name = "skipped_count", nullable = false)
	private int skippedCount;

	@Column(name = "manual_processing_count", nullable = false)
	private int manualProcessingCount;

	@Column(name = "error_message", length = 2000)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

}
