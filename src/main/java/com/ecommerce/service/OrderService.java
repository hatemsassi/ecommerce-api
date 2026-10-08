package com.ecommerce.service;

import com.ecommerce.dto.OrderItemRequest;
import com.ecommerce.dto.OrderRequest;
import com.ecommerce.dto.OrderResponse;
import com.ecommerce.entity.Customer;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.enums.OrderStatus;
import com.ecommerce.entity.enums.PaymentStatus;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.ecommerce.entity.enums.OrderStatus.CANCELLED;
import static com.ecommerce.entity.enums.OrderStatus.CONFIRMED;
import static com.ecommerce.entity.enums.OrderStatus.DELIVERED;
import static com.ecommerce.entity.enums.OrderStatus.PENDING;
import static com.ecommerce.entity.enums.OrderStatus.PROCESSING;
import static com.ecommerce.entity.enums.OrderStatus.SHIPPED;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private static final int LOW_STOCK_THRESHOLD = 10;

	// One-way transitions; statuses not listed as keys (DELIVERED, CANCELLED, REFUNDED) are final.
	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
			PENDING, EnumSet.of(CONFIRMED, CANCELLED),
			CONFIRMED, EnumSet.of(PROCESSING, CANCELLED),
			PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
			SHIPPED, EnumSet.of(DELIVERED)
	));

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final CustomerService customerService;

	public Page<OrderResponse> findAll(Long customerId, OrderStatus status, Pageable pageable) {
		Page<Order> orders;
		if (customerId != null && status != null) {
			orders = orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
		} else if (customerId != null) {
			orders = orderRepository.findByCustomerId(customerId, pageable);
		} else if (status != null) {
			orders = orderRepository.findByStatus(status, pageable);
		} else {
			orders = orderRepository.findAll(pageable);
		}
		return orders.map(OrderResponse::from);
	}

	public OrderResponse findById(Long id) {
		return OrderResponse.from(getOrder(id));
	}

	public OrderResponse findByOrderNumber(String orderNumber) {
		return OrderResponse.from(orderRepository.findByOrderNumber(orderNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber)));
	}

	@Transactional
	public OrderResponse placeOrder(OrderRequest request) {
		Customer customer = customerService.getCustomer(request.getCustomerId());
		String shippingAddress = request.getShippingAddress() != null
				? request.getShippingAddress()
				: customer.getAddress();
		if (shippingAddress == null || shippingAddress.isBlank()) {
			throw new BusinessRuleException("A shipping address is required: the customer has no address on file");
		}

		Order order = Order.builder()
				.customer(customer)
				.shippingAddress(shippingAddress)
				.notes(request.getNotes())
				.build();

		BigDecimal total = BigDecimal.ZERO;
		// A product listed twice is the same managed instance, so the set holds it once.
		Set<Product> orderedProducts = new LinkedHashSet<>();
		// Lock products in id order so two orders sharing products can't deadlock.
		for (OrderItemRequest itemRequest : request.getItems().stream()
				.sorted(Comparator.comparing(OrderItemRequest::getProductId)).toList()) {
			Product product = productRepository.findByIdForUpdate(itemRequest.getProductId())
					.orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));
			if (!product.getActive()) {
				throw new BusinessRuleException("Product '%s' is no longer available".formatted(product.getSku()));
			}
			int quantity = itemRequest.getQuantity();
			if (product.getStockQuantity() < quantity) {
				throw new BusinessRuleException("Insufficient stock for product '%s': %d available, %d requested"
						.formatted(product.getSku(), product.getStockQuantity(), quantity));
			}

			// Stock is reserved when the order is placed, not at shipment.
			product.setStockQuantity(product.getStockQuantity() - quantity);
			orderedProducts.add(product);

			OrderItem item = OrderItem.builder()
					.product(product)
					.quantity(quantity)
					.unitPrice(product.getPrice())
					.build();
			order.addItem(item);
			total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
		}
		order.setTotalAmount(total);
		order.setPayment(Payment.builder()
				.paymentMethod(request.getPaymentMethod())
				.amount(total)
				.build());

		OrderResponse response = OrderResponse.from(orderRepository.save(order));
		warnLowStock(orderedProducts);
		return response;
	}

	@Transactional
	public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
		Order order = lockOrder(id);
		OrderStatus current = order.getStatus();
		if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
			throw new BusinessRuleException("Order %s cannot move from %s to %s"
					.formatted(order.getOrderNumber(), current, newStatus));
		}

		if (newStatus == CANCELLED) {
			restoreStock(order);
			Payment payment = order.getPayment();
			if (payment != null && payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
				payment.setPaymentStatus(PaymentStatus.REFUNDED);
			}
		}
		order.setStatus(newStatus);
		return OrderResponse.from(orderRepository.saveAndFlush(order));
	}

	@Transactional
	public OrderResponse updatePaymentStatus(Long id, PaymentStatus newStatus) {
		Order order = lockOrder(id);
		Payment payment = order.getPayment();
		if (payment == null) {
			throw new ResourceNotFoundException("Payment", "orderId", id);
		}
		// REFUNDED is only reachable by cancelling the order, so stock and payment stay consistent.
		if (newStatus == PaymentStatus.REFUNDED) {
			throw new BusinessRuleException("Payments are refunded by cancelling the order");
		}
		if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
			throw new BusinessRuleException("Payment for order %s is already %s"
					.formatted(order.getOrderNumber(), payment.getPaymentStatus()));
		}
		if (order.getStatus() == CANCELLED) {
			throw new BusinessRuleException("Order %s is cancelled".formatted(order.getOrderNumber()));
		}
		payment.setPaymentStatus(newStatus);
		return OrderResponse.from(order);
	}

	// Warning only: a low remaining stock never blocks the order.
	private void warnLowStock(Set<Product> products) {
		products.stream()
				.filter(product -> product.getStockQuantity() < LOW_STOCK_THRESHOLD)
				.forEach(product -> log.warn("Low stock alert: {} has {} units left",
						product.getName(), product.getStockQuantity()));
	}

	// Atomic "stock = stock + n" updates, in product id order to avoid deadlocks with concurrent orders.
	private void restoreStock(Order order) {
		order.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.forEach(item -> productRepository.increaseStock(item.getProduct().getId(), item.getQuantity()));
	}

	private Order getOrder(Long id) {
		return orderRepository.findWithDetailsById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
	}

	private Order lockOrder(Long id) {
		return orderRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
	}
}
