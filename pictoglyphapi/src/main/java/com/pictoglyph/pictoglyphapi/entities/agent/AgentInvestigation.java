package com.pictoglyph.pictoglyphapi.entities.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_investigation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AgentInvestigation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "symbol_id")
	private Long symbolId;

	@Column(name = "language_id")
	private Long languageId;

	@Column(name = "place_id")
	private Long placeId;

	@Column(length = 1000)
	private String question;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_json", columnDefinition = "JSON")
	private JsonNode resultJson;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}