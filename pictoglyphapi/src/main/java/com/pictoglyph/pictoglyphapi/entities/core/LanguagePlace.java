package com.pictoglyph.pictoglyphapi.entities.core;

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

import static com.pictoglyph.pictoglyphapi.utils.Constants.DATE_START;
import static com.pictoglyph.pictoglyphapi.utils.Constants.DATE_END;


@Entity
@Table(name = "language_place")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LanguagePlace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = DATE_START)
	private Integer dateStart;

	@Column(name = DATE_END)
	private Integer dateEnd;

	private Integer confidence;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "language_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Language language;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Place place;

}
