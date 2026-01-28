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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(
		name = "route_stop",
		uniqueConstraints = @UniqueConstraint(columnNames = {"trade_route_id", "order_index"})
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RouteStop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "order_index", nullable = false)
	private Integer orderIndex;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trade_route_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private TradeRoute tradeRoute;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Place place;
}
