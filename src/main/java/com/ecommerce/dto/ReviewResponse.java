package com.ecommerce.dto;

import com.ecommerce.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

	private Long id;
	private Long productId;
	private Long customerId;
	private String customerName;
	private Integer rating;
	private String comment;
	private LocalDateTime createdAt;

	public static ReviewResponse from(Review review) {
		return ReviewResponse.builder()
				.id(review.getId())
				.productId(review.getProduct().getId())
				.customerId(review.getCustomer().getId())
				.customerName(review.getCustomer().getFirstName() + " " + review.getCustomer().getLastName())
				.rating(review.getRating())
				.comment(review.getComment())
				.createdAt(review.getCreatedAt())
				.build();
	}
}