package com.ecommerce.controller;

import com.ecommerce.config.WebConfig;
import com.ecommerce.dto.ReviewRequest;
import com.ecommerce.dto.ReviewResponse;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(WebConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ReviewControllerTest {

	private static final String URL = "/api/v1/products/10/reviews";

	private final MockMvc mockMvc;

	@MockitoBean
	private ReviewService reviewService;

	@Test
	void should_return201WithReview_when_requestIsValid() throws Exception {
		// Arrange
		when(reviewService.create(eq(10L), any(ReviewRequest.class))).thenReturn(response());

		// Act
		ResultActions result = postReview("{\"customerId\":1,\"rating\":5,\"comment\":\"Great book\"}");

		// Assert
		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(100))
				.andExpect(jsonPath("$.productId").value(10))
				.andExpect(jsonPath("$.customerName").value("Jane Doe"))
				.andExpect(jsonPath("$.rating").value(5));
	}

	@Test
	void should_return400_when_ratingIsAboveFive() throws Exception {
		// Act
		ResultActions result = postReview("{\"customerId\":1,\"rating\":6,\"comment\":\"Great book\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
		verifyNoInteractions(reviewService);
	}

	@Test
	void should_return400_when_ratingIsBelowOne() throws Exception {
		// Act
		ResultActions result = postReview("{\"customerId\":1,\"rating\":0,\"comment\":\"Great book\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
		verifyNoInteractions(reviewService);
	}

	@Test
	void should_return400ListingAllFieldErrors_when_customerIdAndCommentMissing() throws Exception {
		// Act
		ResultActions result = postReview("{\"rating\":3,\"comment\":\"  \"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(2))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'customerId')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'comment')]").exists());
		verifyNoInteractions(reviewService);
	}

	@Test
	void should_return422_when_customerIsNotVerifiedBuyer() throws Exception {
		// Arrange
		when(reviewService.create(eq(10L), any(ReviewRequest.class)))
				.thenThrow(new BusinessRuleException("Customer 1 can only review product 10 after receiving it in a delivered order"));

		// Act
		ResultActions result = postReview("{\"customerId\":1,\"rating\":5,\"comment\":\"Great book\"}");

		// Assert
		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value("Customer 1 can only review product 10 after receiving it in a delivered order"));
	}

	@Test
	void should_return409_when_customerAlreadyReviewedProduct() throws Exception {
		// Arrange
		when(reviewService.create(eq(10L), any(ReviewRequest.class)))
				.thenThrow(new DuplicateResourceException("Review", "customerId", 1L));

		// Act
		ResultActions result = postReview("{\"customerId\":1,\"rating\":5,\"comment\":\"Great book\"}");

		// Assert
		result.andExpect(status().isConflict());
	}

	@Test
	void should_return200WithPage_when_listingReviews() throws Exception {
		// Arrange
		when(reviewService.findByProduct(eq(10L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response())));

		// Act
		ResultActions result = mockMvc.perform(get(URL));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(100))
				.andExpect(jsonPath("$.content[0].comment").value("Great book"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void should_return404_when_listingReviewsOfUnknownProduct() throws Exception {
		// Arrange
		when(reviewService.findByProduct(eq(99L), any(Pageable.class)))
				.thenThrow(new ResourceNotFoundException("Product", "id", 99L));

		// Act
		ResultActions result = mockMvc.perform(get("/api/v1/products/99/reviews"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Product not found with id: 99"));
	}

	private ResultActions postReview(String json) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static ReviewResponse response() {
		return ReviewResponse.builder()
				.id(100L)
				.productId(10L)
				.customerId(1L)
				.customerName("Jane Doe")
				.rating(5)
				.comment("Great book")
				.build();
	}
}