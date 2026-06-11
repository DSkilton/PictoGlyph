package com.pictoglyph.pictoglyphapi.entities.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.pictoglyph.pictoglyphapi.utils.Constants;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

import static com.pictoglyph.pictoglyphapi.utils.Constants.JSON;
import static com.pictoglyph.pictoglyphapi.utils.Constants.LANGUAGE_ID;
import static com.pictoglyph.pictoglyphapi.utils.Constants.MODEL_VERSION_ID;
import static com.pictoglyph.pictoglyphapi.utils.Constants.SYMBOL_ID;

@Entity
@Table(name = "prediction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prediction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	private Double score;

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	@Column(name = Constants.CREATED_AT, nullable = false, updatable = false)
	private Instant createdAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = JSON)
	private JsonNode details;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = MODEL_VERSION_ID, nullable = false)
	@ToString.Exclude
	private ModelVersion modelVersion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = SYMBOL_ID, nullable = false)
	@ToString.Exclude
	private Symbol symbol;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = LANGUAGE_ID, nullable = false)
	@ToString.Exclude
	private Language language;
}
