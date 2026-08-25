package com.pictoglyph.pictoglyphapi.entities.DatasetPreparation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.time.LocalDateTime;

@Entity
@Table(
		name = "dataset_preparation_symbol",
		indexes = {
				@Index(
						name = "idex_dataset_symbol_dataset",
						columnList = "datset_preparation_id"
				),
				@Index(
						name = "idx_dataset_symbol_symbol",
						columnList = "symbol_id"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_dataset_preparation_symbol",
						columnNames = {
								"dataset_preparation_id",
								"symbolid"
						}
				)
		}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "datasetPreparation")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DatasetPreparationSymbol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "dataset_preparation_id",
			nullable = false
	)
	private DatasetPreparation datasetPreparation;

	@Column(name = "symbol_id", nullable = false)
	private Long symbolId;

	@Column(name = "added_at", nullable = false)
	private LocalDateTime addedAt;

	@PrePersist
	void onCreate() {
		if (addedAt == null) {
			addedAt = LocalDateTime.now();
		}
	}
}
