package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.AgentTool;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Place;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.pictoglyph.pictoglyphapi.repositories.core.PlaceRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlaceTool implements AgentTool {
	private final PlaceRepository placeRepository;
	@Override
	public String getName() {
		return "PlaceTool";
	}

	@Override
	public List<Evidence> execute(AgentContext context) {
		if (context.placeId() == null) {
			return List.of();
		}

		Optional<Place> optionalPlace = placeRepository.findById(context.placeId());

		if (optionalPlace.isEmpty()) {
			return List.of(
					Evidence.builder()
							.source(getName())
							.description("No place found for id: " + context.placeId())
							.confidence(0.10)
							.build()
			);
		}

		Place place = optionalPlace.get();

		return List.of(
				Evidence.builder()
						.source(getName())
						.description(buildPlaceEvidenceDescription(place))
						.confidence(calculateConfidence(place))
						.build()
		);
	}

	private String buildPlaceEvidenceDescription(Place place){
		return "Place '%s' is located in '%s' with coordinates latitude '%s' and longitude '%s'"
				.formatted(
						place.getName(),
						place.getCountry(),
						place.getLatitude(),
						place.getLongitude()
				);
	}

	private double calculateConfidence(Place place) {
		boolean hasName = place.getName() != null && !place.getName().isBlank();
		boolean hasCountry = place.getCountry() != null && !place.getCountry().isBlank();
		boolean hasCoordinates = place.getLatitude() != null && place.getLongitude() != null;

		if (hasName && hasCountry && hasCoordinates) {
			return 0.80;
		}

		if (hasName && hasCountry) {
			return 0.60;
		}

		return 0.35;
	}
}
