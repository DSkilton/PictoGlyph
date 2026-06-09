package com.pictoglyph.pictoglyphapi.entities.core;

import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import static com.pictoglyph.pictoglyphapi.utils.Constants.NAME;
import static com.pictoglyph.pictoglyphapi.utils.Constants.KIND;
import static com.pictoglyph.pictoglyphapi.utils.Constants.META_DATA;


@Entity
@Table(name = "port",
		uniqueConstraints = @UniqueConstraint(columnNames = {"place_id", "name", "kind"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Port {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = NAME, nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = KIND, nullable = false)
	private TravelMode kind;

	@Column(name = META_DATA, columnDefinition = "JSON")
	private JsonNode metaData;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	@ToString.Exclude
	private Place place;
}
