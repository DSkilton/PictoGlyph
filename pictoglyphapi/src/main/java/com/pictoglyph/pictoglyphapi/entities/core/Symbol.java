package com.pictoglyph.pictoglyphapi.entities.core;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import static com.pictoglyph.pictoglyphapi.utils.Constants.IMAGE_PATH;
import static com.pictoglyph.pictoglyphapi.utils.Constants.SYMBOL_CODE;
import static com.pictoglyph.pictoglyphapi.utils.Constants.JSON;
import static com.pictoglyph.pictoglyphapi.utils.Constants.LANGUAGE_ID;

@Entity
@Table(name = "symbol")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Symbol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = IMAGE_PATH, nullable = false)
	private String imagePath;

	@Column(name = SYMBOL_CODE, nullable = false)
	private String symbolCode;

	@Column(columnDefinition = JSON)
	private JsonNode features;

	@Column(columnDefinition = JSON)
	private JsonNode meta;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = LANGUAGE_ID, nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Language language;



}
