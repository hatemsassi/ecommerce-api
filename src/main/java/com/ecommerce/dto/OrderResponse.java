package com.ecommerce.dto;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

	private Long id;
	private String orderNumber;
	private Long customerId;
	private String customerName;
	private OrderStatus status;
	private BigDecimal totalAmount;
	private String shippingAddress;
	private String notes;
	private List<OrderItemResponse> items;
	private PaymentResponse payment;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static OrderResponse from(Order order) {
		return OrderResponse.builder()
				.id(order.getId())
				.orderNumber(order.getOrderNumber())
				.customerId(order.getCustomer().getId())
				.customerName(order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName())
				.status(order.getStatus())
				.totalAmount(order.getTotalAmount())
				.shippingAddress(order.getShippingAddress())
				.notes(order.getNotes())
				.items(order.getItems().stream().map(OrderItemResponse::from).toList())
				.payment(order.getPayment() == null ? null : PaymentResponse.from(order.getPayment()))
				.createdAt(order.getCreatedAt())
				.updatedAt(order.getUpdatedAt())
				.build();
	}
}
