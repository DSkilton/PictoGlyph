package com.pictoglyph.pictoglyphapi.ingestion.research;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "research_source_record", uniqueConstraints = @UniqueConstraint(
		name = "uk_research_record_source_key", columnNames = {"source_id", "record_key"}
))
@Getter
@Setter
@NoArgsConstructor
public class ResearchSourceRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_id", nullable = false, length = 100)
	private String sourceId;

	@Column(name = "record_key", nullable = false, length = 255)
	private String recordKey;

	@Column(name = "record_type", nullable = false, length = 60)
	private String recordType;

	@Column(name = "source_url", nullable = false, columnDefinition = "text")
	private String sourceUrl;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "archaeological_context", nullable = false, columnDefinition = "jsonb")
	private JsonNode archaeologicalContext;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_record", nullable = false, columnDefinition = "jsonb")
	private JsonNode rawRecord;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "provenance", nullable = false, columnDefinition = "jsonb")
	private JsonNode provenance;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (lastSeenAt == null) {
			lastSeenAt = createdAt;
		}
	}
}
