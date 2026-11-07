package com.pictoglyph.pictoglyphapi.ingestion.api;

import com.pictoglyph.pictoglyphapi.entities.enums.IngestionReviewStatus;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewItemService;
import com.pictoglyph.pictoglyphapi.ingestion.IngestionReviewReprocessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IngestionReviewControllerTest {

	@Mock
	private IngestionReviewItemService reviewItemService;

	@Mock
	private IngestionReviewReprocessingService reprocessingService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new IngestionReviewController(reviewItemService, reprocessingService)).build();
	}

	@Test
	void shouldDismissReviewItemUsingPathId() throws Exception {
		IngestionReviewItemResponse response = new IngestionReviewItemResponse(55L, 100L, 0, "Missing symbol code", null, IngestionReviewStatus.DISMISSED, "Not a valid sign", null, null);

		when(reviewItemService.update(eq(55L), any(UpdateIngestionReviewItemRequest.class))).thenReturn(response);

		mockMvc.perform(patch("/ingestion/review-items/55")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"status": "DISMISSED",
									"resolutionNotes": "Not a valid sign"
								}
								"""))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(handler().handlerType(IngestionReviewController.class))
				.andExpect(handler().methodName("update"))
				.andExpect(jsonPath("$.id").value(55))
				.andExpect(jsonPath("$.status").value("DISMISSED"));

		verify(reviewItemService).update(eq(55L), argThat(request -> request.status() == IngestionReviewStatus.DISMISSED && "Not a valid sign".equals(request.resolutionNotes())));
	}

	@Test
	void shouldRetrievePendingReviewItems() throws Exception {
		when(reviewItemService.findByStatus(IngestionReviewStatus.PENDING)).thenReturn(List.of());

		mockMvc.perform(get("/ingestion/review-items"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(handler().methodName("findByStatus"))
				.andExpect(jsonPath("$.length()").value(0));

		verify(reviewItemService).findByStatus(IngestionReviewStatus.PENDING);
	}

	@Test
	void shouldRejectPatchWithoutReviewItemId() throws Exception {
		mockMvc.perform(patch("/ingestion/review-items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"status": "DISMISSED"
								}
								"""))
				.andDo(print())
				.andExpect(status().isMethodNotAllowed());
	}
}
