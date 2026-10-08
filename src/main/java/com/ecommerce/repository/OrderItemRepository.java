package com.ecommerce.repository;

import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByOrderId(Long orderId);

	// Verified-purchase check: has this customer had the product in an order with the given status?
	boolean existsByProductIdAndOrderCustomerIdAndOrderStatus(Long productId, Long customerId, OrderStatus status);
}