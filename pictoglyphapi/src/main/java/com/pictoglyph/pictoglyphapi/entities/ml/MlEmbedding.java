package com.pictoglyph.pictoglyphapi.entities.ml;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
		name = "ml_embedding",
		indexes = {
				@Index(
						name = "idx_ml_embedding_symbol",
						columnList = "symbol_id"
				),
				@Index(
						name = "idx_ml_embedding_model",
						columnList = "model_name, model_version"
				),
				@Index(
						name = "idx_ml_embedding_checksum",
						columnList = "input_checksum"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_ml_embedding_job_model",
						columnNames = {
								"processing_job_id",
								"model_name",
								"model_version"
						}
				)
		}
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MlEmbedding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "processing_job_id", nullable = false)
	private Long processingJobId;

	@Column(name = "symbol_id", nullable = false)
	private Long symbolId;

	@Column(name = "model_name", nullable = false, length = 150)
	private String modelName;

	@Column(name = "model_version", nullable = false)
	private String modelVersion;

	@Column(name = "model_profile", nullable = false, length = 150)
	private String modelProfile;

	@Column(name = "embedding_dimension", nullable = false)
	private int embeddingDimension;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "embedding", nullable = false, columnDefinition = "jsonb")
	private JsonNode embedding;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "preprocessing", columnDefinition = "jsonb")
	private JsonNode preprocessing;

	@Column(name = "input_checksum", nullable = false, length = 128)
	private String inputChecksum;

	@Column(name = "processed_at", nullable = false)
	private LocalDateTime processedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}

