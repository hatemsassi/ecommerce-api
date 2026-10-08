package com.ecommerce.controller;

import com.ecommerce.dto.ReviewRequest;
import com.ecommerce.dto.ReviewResponse;
import com.ecommerce.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	@GetMapping
	public Page<ReviewResponse> findByProduct(@PathVariable Long productId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return reviewService.findByProduct(productId, pageable);
	}

	// No Location header: there is no endpoint for a single review.
	@PostMapping
	public ResponseEntity<ReviewResponse> create(@PathVariable Long productId,
			@Valid @RequestBody ReviewRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(productId, request));
	}
}