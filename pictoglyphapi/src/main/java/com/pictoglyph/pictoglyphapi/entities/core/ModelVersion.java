package com.pictoglyph.pictoglyphapi.entities.core;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

import static com.pictoglyph.pictoglyphapi.utils.Constants.CREATED_AT;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JSON;

@Entity
@Table(name = "model_version")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModelVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	@Column(name = CREATED_AT, nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false, unique = true)
	private String version;

	@Column(columnDefinition = JSON)
	private JsonNode notes;

}
