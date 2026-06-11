package com.pictoglyph.pictoglyphapi.entities.core;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.math.BigDecimal;

import static com.pictoglyph.pictoglyphapi.utils.Constants.JSON;

@Entity
@Table(name = "place")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Place {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String country;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = JSON)
	private JsonNode meta;
}
