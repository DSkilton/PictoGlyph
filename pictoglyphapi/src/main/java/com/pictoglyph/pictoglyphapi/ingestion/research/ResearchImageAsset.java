package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "research_image_asset", uniqueConstraints = @UniqueConstraint(
		name = "uk_research_asset_record_key", columnNames = {"research_source_record_id", "asset_key"}
))
@Getter
@Setter
@NoArgsConstructor
public class ResearchImageAsset {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "research_source_record_id", nullable = false)
	private ResearchSourceRecord researchSourceRecord;

	@Column(name = "asset_key", nullable = false, length = 100)
	private String assetKey;

	@Column(name = "source_path", nullable = false, columnDefinition = "text")
	private String sourcePath;

	@Column(name = "image_kind", nullable = false, length = 100)
	private String imageKind;

	@Column(name = "rights_statement", columnDefinition = "text")
	private String rights;

	@Column(name = "sha256", length = 64)
	private String checksumSha256;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "analysis", nullable = false, columnDefinition = "jsonb")
	private JsonNode analysis;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
