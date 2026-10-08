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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final ProductService productService;
	private final CustomerService customerService;

	public Page<ReviewResponse> findByProduct(Long productId, Pageable pageable) {
		// Distinguishes an unknown product (404) from a product with no reviews yet (empty page).
		if (!productRepository.existsById(productId)) {
			throw new ResourceNotFoundException("Product", "id", productId);
		}
		return reviewRepository.findByProductId(productId, pageable).map(ReviewResponse::from);
	}

	// Inactive products stay reviewable: past buyers can still review what they received.
	@Transactional
	public ReviewResponse create(Long productId, ReviewRequest request) {
		Product product = productService.getProduct(productId);
		Customer customer = customerService.getCustomer(request.getCustomerId());
		if (reviewRepository.existsByProductIdAndCustomerId(productId, customer.getId())) {
			throw new DuplicateResourceException("Review", "customerId", customer.getId());
		}
		if (!orderItemRepository.existsByProductIdAndOrderCustomerIdAndOrderStatus(
				productId, customer.getId(), OrderStatus.DELIVERED)) {
			throw new BusinessRuleException("Customer %d can only review product %d after receiving it in a delivered order"
					.formatted(customer.getId(), productId));
		}
		Review review = Review.builder()
				.product(product)
				.customer(customer)
				.rating(request.getRating())
				.comment(request.getComment())
				.build();
		return ReviewResponse.from(reviewRepository.save(review));
	}
}