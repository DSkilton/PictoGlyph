package com.pictoglyph.pictoglyphapi.agent.tools;

import com.pictoglyph.pictoglyphapi.agent.AgentContext;
import com.pictoglyph.pictoglyphapi.agent.Evidence;
import com.pictoglyph.pictoglyphapi.entities.core.Place;
import com.pictoglyph.pictoglyphapi.repositories.core.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlaceToolTest {

	@Mock
	private PlaceRepository placeRepository;

	@InjectMocks
	private PlaceTool placeTool;

	@Test
	void executeShouldReturnNoEvidenceWhenPlaceIdIsNull() {
		AgentContext context = AgentContext.builder()
				.placeId(null)
				.question("What is the likely place context?")
				.build();

		List<Evidence> evidence = placeTool.execute(context);

		assertThat(evidence).isEmpty();

		verifyNoInteractions(placeRepository);
	}

	@Test
	void executeShouldReturnLowConfidenceEvidenceWhenPlaceCannotBeFound() {
		AgentContext context = AgentContext.builder()
				.placeId(1L)
				.question("What is the likely place context?")
				.build();

		when(placeRepository.findById(1L)).thenReturn(Optional.empty());

		List<Evidence> evidence = placeTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence placeEvidence = evidence.get(0);

		assertThat(placeEvidence.source()).isEqualTo("PlaceTool");
		assertThat(placeEvidence.description()).isEqualTo("No place found for id: 1");
		assertThat(placeEvidence.confidence()).isEqualTo(0.10);

		verify(placeRepository).findById(1L);
	}

	@Test
	void executeShouldReturnPlaceEvidenceWhenPlaceExists() {
		Place place = Place.builder()
				.id(1L)
				.name("Memphis")
				.country("Egypt")
				.latitude(new BigDecimal("29.8440"))
				.longitude(new BigDecimal("31.2510"))
				.build();

		AgentContext context = AgentContext.builder()
				.placeId(1L)
				.question("What is the likely place context")
				.build();

		when(placeRepository.findById(1L)).thenReturn(Optional.of(place));

		List<Evidence> evidence = placeTool.execute(context);

		assertThat(evidence).hasSize(1);

		Evidence placeEvidence = evidence.get(0);

		assertThat(placeEvidence.source()).isEqualTo("PlaceTool");
		assertThat(placeEvidence.description())
				.isEqualTo("Place 'Memphis' is located in 'Egypt' with coordinates latitude '29.8440' and longitude '31.2510'");
		assertThat(placeEvidence.confidence()).isEqualTo(0.80);

		verify(placeRepository).findById(1L);
	}
}
