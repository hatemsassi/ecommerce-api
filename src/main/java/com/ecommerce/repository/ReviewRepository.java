package com.ecommerce.repository;

import com.ecommerce.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	@EntityGraph(attributePaths = "customer")
	Page<Review> findByProductId(Long productId, Pageable pageable);

	boolean existsByProductIdAndCustomerId(Long productId, Long customerId);
}