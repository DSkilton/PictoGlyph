package com.pictoglyph.pictoglyphapi.entities.core;

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

@Entity
@Table(
		name = "route_leg",
		uniqueConstraints = @UniqueConstraint(columnNames = {"trade_route_id", "from_stop_id", "to_stop_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RouteLeg {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long Id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TravelMode mode;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trade_route_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private TradeRoute tradeRoute;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_stop_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private RouteStop fromStop;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_stop_id", nullable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private RouteStop toStop;
}
