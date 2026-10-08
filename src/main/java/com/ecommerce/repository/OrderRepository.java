package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = {"customer", "items", "items.product", "payment"})
	Optional<Order> findWithDetailsById(Long id);

	@EntityGraph(attributePaths = {"customer", "items", "items.product", "payment"})
	Optional<Order> findByOrderNumber(String orderNumber);

	Page<Order> findByCustomerId(Long customerId, Pageable pageable);

	Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
