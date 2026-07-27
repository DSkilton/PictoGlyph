package com.pictoglyph.pictoglyphapi.entities.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_source_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ApiSourceProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "profile_name", nullable = false, unique = true, length = 200)
	private String profileName;

	@Column(name = "source_name", nullable = false, length = 255)
	private String sourceName;

	@Column(name = "api_url", nullable = false, length = 1000)
	private String apiUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApiSourceProfileStatus status;

	@Column(name = "item_array_field", length = 500)
	private String itemArrayField;

	@Column(name = "symbol_code_field", nullable = false, length = 500)
	private String symbolCodeField;

	@Column(name = "image_path_field", nullable = false, length = 500)
	private String imagePathField;

	@Column(name = "title_field", length = 500)
	private String titleField;

	@Column(name = "description_field", length = 500)
	private String descriptionField;

	@Column(name = "place_field", length = 500)
	private String placeField;

	@Column(name = "period_field", length = 500)
	private String periodField;

	@Column(name = "date_start_field", length = 500)
	private String dateStartField;

	@Column(name = "date_end_field", length = 500)
	private String dateEndField;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "validated_at")
	private LocalDateTime validatedAt;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();

		if (createdAt == null) {
			createdAt = now;
		}

		if (updatedAt == null) {
			updatedAt = now;
		}

		if (status == null) {
			status = ApiSourceProfileStatus.DRAFT;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}