package com.pictoglyph.pictoglyphapi;

import com.pictoglyph.pictoglyphapi.dataset.DatasetPreparationService;
import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionJob;
import com.pictoglyph.pictoglyphapi.entities.ingestion.IngestionReviewItem;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewItemService;
import com.pictoglyph.pictoglyphapi.ingestion.api.IngestionReviewItemResponse;
import com.pictoglyph.pictoglyphapi.ingestion.api.UpdateIngestionReviewItemRequest;
import com.pictoglyph.pictoglyphapi.repositories.ingestion.IngestionReviewItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IngestionReviewItemRepositoryTest {

	@Mock
	private IngestionReviewItemRepository ingestionRepo;

	@Mock
	private DatasetPreparationService datasetPreparationService;

	private IngestionReviewItemService service;

	@BeforeEach
	void setUp() {
		service = new IngestionReviewItemService(ingestionRepo, datasetPreparationService);
	}

	@Test
	void shouldRevalidateDatasetAfterReviewDismissal() {
		IngestionJob ingestionJob = IngestionJob.builder()
				.id(100L)
				.build();

		IngestionReviewItem reviewItem = IngestionReviewItem.builder()
				.id(55L)
				.ingestionJob(ingestionJob)
				.itemIndex(0)
				.reason("Missing symbol code")
				.rawItem(new ObjectMapper().createObjectNode())
				.status(IngestionReviewStatus.PENDING)
				.build();

		when(ingestionRepo.findById(55L)).thenReturn(Optional.of(reviewItem));
		when(ingestionRepo.save(reviewItem)).thenReturn(reviewItem);

		UpdateIngestionReviewItemRequest request = new UpdateIngestionReviewItemRequest(IngestionReviewStatus.DISMISSED, "Reviewed and intentionally excluded");
		IngestionReviewItemResponse response = service.update(55L, request);

		assertThat(response.status()).isEqualTo(IngestionReviewStatus.DISMISSED);
		assertThat(response.resolvedAt()).isNotNull();

		verify(ingestionRepo).flush();
		verify(datasetPreparationService).revalidateForIngestionJob(100L);
	}
}
