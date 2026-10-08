package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = {"customer", "items", "items.product", "payment"})
	Optional<Order> findWithDetailsById(Long id);

	@EntityGraph(attributePaths = {"customer", "items", "items.product", "payment"})
	Optional<Order> findByOrderNumber(String orderNumber);

	// Row lock so concurrent status changes (e.g. two cancels) are applied one at a time.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	Optional<Order> findByIdForUpdate(Long id);

	Page<Order> findByCustomerId(Long customerId, Pageable pageable);

	Page<Order> findByStatus(OrderStatus status, Pageable pageable);

	Page<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status, Pageable pageable);

	boolean existsByCustomerId(Long customerId);
}
