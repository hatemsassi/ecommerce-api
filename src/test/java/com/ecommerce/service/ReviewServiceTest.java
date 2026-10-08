package com.ecommerce.service;

import com.ecommerce.dto.ReviewRequest;
import com.ecommerce.dto.ReviewResponse;
import com.ecommerce.entity.Customer;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Review;
import com.ecommerce.entity.enums.OrderStatus;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	private static final Pageable PAGEABLE = PageRequest.of(0, 20);

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductService productService;

	@Mock
	private CustomerService customerService;

	@InjectMocks
	private ReviewService reviewService;

	private final Product product = Product.builder().id(10L).name("Clean Code").sku("BOOK-CLN-001").build();
	private final Customer customer = Customer.builder()
			.id(1L).firstName("Jane").lastName("Doe").email("jane@example.com").build();

	@Test
	void should_createReview_when_customerIsVerifiedBuyerWithoutPriorReview() {
		// Arrange
		when(productService.getProduct(10L)).thenReturn(product);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(reviewRepository.existsByProductIdAndCustomerId(10L, 1L)).thenReturn(false);
		when(orderItemRepository.existsByProductIdAndOrderCustomerIdAndOrderStatus(10L, 1L, OrderStatus.DELIVERED))
				.thenReturn(true);
		when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
			Review saved = invocation.getArgument(0);
			saved.setId(100L);
			return saved;
		});

		// Act
		ReviewResponse result = reviewService.create(10L, request(5, "Great book"));

		// Assert
		assertThat(result.getId()).isEqualTo(100L);
		assertThat(result.getProductId()).isEqualTo(10L);
		assertThat(result.getCustomerId()).isEqualTo(1L);
		assertThat(result.getCustomerName()).isEqualTo("Jane Doe");
		assertThat(result.getRating()).isEqualTo(5);
		assertThat(result.getComment()).isEqualTo("Great book");
	}

	@Test
	void should_throwResourceNotFound_when_productDoesNotExist() {
		// Arrange
		when(productService.getProduct(99L)).thenThrow(new ResourceNotFoundException("Product", "id", 99L));

		// Act & Assert
		assertThatThrownBy(() -> reviewService.create(99L, request(5, "Great book")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Product not found with id: 99");
		verify(reviewRepository, never()).save(any());
	}

	@Test
	void should_throwResourceNotFound_when_customerDoesNotExist() {
		// Arrange
		when(productService.getProduct(10L)).thenReturn(product);
		when(customerService.getCustomer(1L)).thenThrow(new ResourceNotFoundException("Customer", "id", 1L));

		// Act & Assert
		assertThatThrownBy(() -> reviewService.create(10L, request(5, "Great book")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Customer not found with id: 1");
		verify(reviewRepository, never()).save(any());
	}

	@Test
	void should_throwDuplicateResource_when_customerAlreadyReviewedProduct() {
		// Arrange
		when(productService.getProduct(10L)).thenReturn(product);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(reviewRepository.existsByProductIdAndCustomerId(10L, 1L)).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> reviewService.create(10L, request(4, "Second thoughts")))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("Review already exists with customerId: 1");
		verify(reviewRepository, never()).save(any());
	}

	@Test
	void should_throwBusinessRule_when_customerHasNoDeliveredOrderForProduct() {
		// Arrange
		when(productService.getProduct(10L)).thenReturn(product);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(reviewRepository.existsByProductIdAndCustomerId(10L, 1L)).thenReturn(false);
		when(orderItemRepository.existsByProductIdAndOrderCustomerIdAndOrderStatus(10L, 1L, OrderStatus.DELIVERED))
				.thenReturn(false);

		// Act & Assert
		assertThatThrownBy(() -> reviewService.create(10L, request(5, "Great book")))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Customer 1 can only review product 10 after receiving it in a delivered order");
		verify(reviewRepository, never()).save(any());
	}

	@Test
	void should_checkOnlyDeliveredOrders_when_verifyingPurchase() {
		// Arrange
		when(productService.getProduct(10L)).thenReturn(product);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(reviewRepository.existsByProductIdAndCustomerId(10L, 1L)).thenReturn(false);

		// Act
		assertThatThrownBy(() -> reviewService.create(10L, request(5, "Great book")))
				.isInstanceOf(BusinessRuleException.class);

		// Assert
		verify(orderItemRepository).existsByProductIdAndOrderCustomerIdAndOrderStatus(
				eq(10L), eq(1L), eq(OrderStatus.DELIVERED));
	}

	@Test
	void should_returnPageOfReviews_when_productExists() {
		// Arrange
		Review review = Review.builder().id(100L).product(product).customer(customer).rating(4).comment("Solid").build();
		when(productRepository.existsById(10L)).thenReturn(true);
		when(reviewRepository.findByProductId(10L, PAGEABLE)).thenReturn(new PageImpl<>(List.of(review)));

		// Act
		Page<ReviewResponse> result = reviewService.findByProduct(10L, PAGEABLE);

		// Assert
		assertThat(result.getContent()).extracting(ReviewResponse::getId, ReviewResponse::getRating)
				.containsExactly(tuple(100L, 4));
	}

	@Test
	void should_throwResourceNotFound_when_listingReviewsOfUnknownProduct() {
		// Arrange
		when(productRepository.existsById(99L)).thenReturn(false);

		// Act & Assert
		assertThatThrownBy(() -> reviewService.findByProduct(99L, PAGEABLE))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Product not found with id: 99");
		verify(reviewRepository, never()).findByProductId(anyLong(), any());
	}

	private static ReviewRequest request(int rating, String comment) {
		return ReviewRequest.builder().customerId(1L).rating(rating).comment(comment).build();
	}
}