package com.ecommerce.controller;

import com.ecommerce.dto.OrderRequest;
import com.ecommerce.dto.OrderResponse;
import com.ecommerce.dto.OrderStatusUpdateRequest;
import com.ecommerce.dto.PaymentStatusUpdateRequest;
import com.ecommerce.entity.enums.OrderStatus;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@GetMapping
	public Page<OrderResponse> findAll(@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) OrderStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return orderService.findAll(customerId, status, pageable);
	}

	@GetMapping("/{id}")
	public OrderResponse findById(@PathVariable Long id) {
		return orderService.findById(id);
	}

	@GetMapping("/number/{orderNumber}")
	public OrderResponse findByOrderNumber(@PathVariable String orderNumber) {
		return orderService.findByOrderNumber(orderNumber);
	}

	@PostMapping
	public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
		OrderResponse created = orderService.placeOrder(request);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri()).body(created);
	}

	// Setting status to CANCELLED restores stock and refunds a completed payment.
	@PutMapping("/{id}/status")
	public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
		return orderService.updateStatus(id, request.getStatus());
	}

	@PutMapping("/{id}/payment/status")
	public OrderResponse updatePaymentStatus(@PathVariable Long id,
			@Valid @RequestBody PaymentStatusUpdateRequest request) {
		return orderService.updatePaymentStatus(id, request.getPaymentStatus());
	}
}
