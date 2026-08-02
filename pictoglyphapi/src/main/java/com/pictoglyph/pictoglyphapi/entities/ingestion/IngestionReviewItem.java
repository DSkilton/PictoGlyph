package com.pictoglyph.pictoglyphapi.entities.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.JsonNodePathReader;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_review_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IngestionReviewItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ingestion_job_id", nullable = false)
	@ToString.Exclude
	private IngestionJob ingestionJob;

	@Column(name = "item_index", nullable = false)
	private int itemIndex;

	@Column(nullable = false, length = 2000)
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_item", nullable = false, columnDefinition = "jsonb")
	private JsonNode rawItem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IngestionReviewStatus status;

	@Column(name = "resolution_notes", length = 2000)
	private String resolutionNotes;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}

		if (status == null) {
			status = IngestionReviewStatus.PENDING;
		}
	}
}
