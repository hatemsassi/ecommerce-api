package com.ecommerce.service;

import com.ecommerce.dto.OrderItemRequest;
import com.ecommerce.dto.OrderRequest;
import com.ecommerce.dto.OrderResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Customer;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.enums.OrderStatus;
import com.ecommerce.entity.enums.PaymentMethod;
import com.ecommerce.entity.enums.PaymentStatus;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	private static final Pageable PAGEABLE = PageRequest.of(0, 20);

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CustomerService customerService;

	@InjectMocks
	private OrderService orderService;

	private final Category category = Category.builder().id(1L).name("Books").slug("books").build();
	private final Customer customer = Customer.builder()
			.id(1L).firstName("Jane").lastName("Doe").email("jane@example.com").address("1 Main St").build();

	// --- queries ---

	@Test
	void should_filterByCustomerAndStatus_when_bothProvided() {
		// Arrange
		when(orderRepository.findByCustomerIdAndStatus(1L, OrderStatus.PENDING, PAGEABLE))
				.thenReturn(new PageImpl<>(List.of(order(100L, OrderStatus.PENDING))));

		// Act
		Page<OrderResponse> result = orderService.findAll(1L, OrderStatus.PENDING, PAGEABLE);

		// Assert
		assertThat(result.getContent()).extracting(OrderResponse::getId).containsExactly(100L);
	}

	@Test
	void should_returnAllOrders_when_noFilterProvided() {
		// Arrange
		when(orderRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(order(100L, OrderStatus.PENDING))));

		// Act
		Page<OrderResponse> result = orderService.findAll(null, null, PAGEABLE);

		// Assert
		assertThat(result.getTotalElements()).isEqualTo(1);
		verify(orderRepository, never()).findByCustomerId(anyLong(), any());
		verify(orderRepository, never()).findByStatus(any(), any());
	}

	@Test
	void should_returnOrder_when_idExists() {
		// Arrange
		when(orderRepository.findWithDetailsById(100L)).thenReturn(Optional.of(order(100L, OrderStatus.PENDING)));

		// Act
		OrderResponse result = orderService.findById(100L);

		// Assert
		assertThat(result.getId()).isEqualTo(100L);
		assertThat(result.getCustomerName()).isEqualTo("Jane Doe");
	}

	@Test
	void should_throwResourceNotFound_when_orderNumberDoesNotExist() {
		// Arrange
		when(orderRepository.findByOrderNumber("ORD-MISSING")).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> orderService.findByOrderNumber("ORD-MISSING"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Order not found with orderNumber: ORD-MISSING");
	}

	// --- placeOrder ---

	@Test
	void should_placeOrderAndReserveStock_when_requestIsValid() {
		// Arrange
		Product book = product(10L, "SKU-BOOK", "10.00", 5);
		Product pen = product(11L, "SKU-PEN", "2.50", 20);
		OrderRequest request = orderRequest(List.of(item(11L, 4), item(10L, 2)), null);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
		when(productRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(pen));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		OrderResponse result = orderService.placeOrder(request);

		// Assert
		assertThat(result.getTotalAmount()).isEqualByComparingTo("30.00");
		assertThat(result.getShippingAddress()).isEqualTo("1 Main St");
		assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
		assertThat(result.getItems()).extracting(i -> i.getProductId()).containsExactly(10L, 11L);
		assertThat(result.getPayment().getAmount()).isEqualByComparingTo("30.00");
		assertThat(result.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(book.getStockQuantity()).isEqualTo(3);
		assertThat(pen.getStockQuantity()).isEqualTo(16);
	}

	@Test
	void should_throwResourceNotFound_when_placeOrderForUnknownCustomer() {
		// Arrange
		OrderRequest request = orderRequest(List.of(item(10L, 1)), null);
		request.setCustomerId(99L);
		when(customerService.getCustomer(99L)).thenThrow(new ResourceNotFoundException("Customer", "id", 99L));

		// Act & Assert
		assertThatThrownBy(() -> orderService.placeOrder(request))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Customer not found with id: 99");
		verify(productRepository, never()).findByIdForUpdate(anyLong());
		verify(orderRepository, never()).save(any());
	}

	@Test
	void should_throwBusinessRule_when_placeOrderWithInsufficientStock() {
		// Arrange
		Product book = product(10L, "SKU-BOOK", "10.00", 2);
		OrderRequest request = orderRequest(List.of(item(10L, 3)), null);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

		// Act & Assert
		assertThatThrownBy(() -> orderService.placeOrder(request))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Insufficient stock for product 'SKU-BOOK': 2 available, 3 requested");
		assertThat(book.getStockQuantity()).isEqualTo(2);
		verify(orderRepository, never()).save(any());
	}

	@Test
	void should_throwBusinessRule_when_placeOrderWithInactiveProduct() {
		// Arrange
		Product book = product(10L, "SKU-BOOK", "10.00", 5);
		book.setActive(false);
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

		// Act & Assert
		assertThatThrownBy(() -> orderService.placeOrder(orderRequest(List.of(item(10L, 1)), null)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("no longer available");
		verify(orderRepository, never()).save(any());
	}

	@Test
	void should_throwResourceNotFound_when_placeOrderWithUnknownProduct() {
		// Arrange
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> orderService.placeOrder(orderRequest(List.of(item(99L, 1)), null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Product not found with id: 99");
	}

	@Test
	void should_throwBusinessRule_when_noShippingAddressAvailable() {
		// Arrange
		customer.setAddress(null);
		when(customerService.getCustomer(1L)).thenReturn(customer);

		// Act & Assert
		assertThatThrownBy(() -> orderService.placeOrder(orderRequest(List.of(item(10L, 1)), null)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("shipping address is required");
		verify(productRepository, never()).findByIdForUpdate(anyLong());
	}

	@Test
	void should_useRequestShippingAddress_when_provided() {
		// Arrange
		when(customerService.getCustomer(1L)).thenReturn(customer);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product(10L, "SKU-BOOK", "10.00", 5)));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		OrderResponse result = orderService.placeOrder(orderRequest(List.of(item(10L, 1)), "9 Other Rd"));

		// Assert
		assertThat(result.getShippingAddress()).isEqualTo("9 Other Rd");
	}

	// --- updateStatus / cancel ---

	@Test
	void should_cancelOrderRestoreStockAndRefund_when_orderIsPendingAndPaid() {
		// Arrange
		Order order = order(100L, OrderStatus.PENDING);
		order.getPayment().setPaymentStatus(PaymentStatus.COMPLETED);
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
		when(orderRepository.saveAndFlush(order)).thenReturn(order);

		// Act
		OrderResponse result = orderService.updateStatus(100L, OrderStatus.CANCELLED);

		// Assert
		assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(result.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
		verify(productRepository).increaseStock(10L, 2);
	}

	@Test
	void should_throwBusinessRule_when_cancelAlreadyDeliveredOrder() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.DELIVERED)));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.CANCELLED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("cannot move from DELIVERED to CANCELLED");
		verify(productRepository, never()).increaseStock(anyLong(), anyInt());
		verify(orderRepository, never()).saveAndFlush(any());
	}

	@Test
	void should_throwBusinessRule_when_cancelAlreadyCancelledOrder() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.CANCELLED)));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.CANCELLED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("cannot move from CANCELLED to CANCELLED");
		verify(productRepository, never()).increaseStock(anyLong(), anyInt());
		verify(orderRepository, never()).saveAndFlush(any());
	}

	@Test
	void should_advanceStatusWithoutTouchingStock_when_transitionIsAllowed() {
		// Arrange
		Order order = order(100L, OrderStatus.SHIPPED);
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
		when(orderRepository.saveAndFlush(order)).thenReturn(order);

		// Act
		OrderResponse result = orderService.updateStatus(100L, OrderStatus.DELIVERED);

		// Assert
		assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
		verify(productRepository, never()).increaseStock(anyLong(), anyInt());
	}

	@Test
	void should_throwBusinessRule_when_skippingStatus() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.PENDING)));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.SHIPPED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("cannot move from PENDING to SHIPPED");
	}

	@Test
	void should_throwResourceNotFound_when_updateStatusOfUnknownOrder() {
		// Arrange
		when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> orderService.updateStatus(99L, OrderStatus.CANCELLED))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Order not found with id: 99");
	}

	// --- updatePaymentStatus ---

	@Test
	void should_completePayment_when_paymentIsPending() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.PENDING)));

		// Act
		OrderResponse result = orderService.updatePaymentStatus(100L, PaymentStatus.COMPLETED);

		// Assert
		assertThat(result.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
	}

	@Test
	void should_throwBusinessRule_when_paymentSetToRefundedDirectly() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.PENDING)));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updatePaymentStatus(100L, PaymentStatus.REFUNDED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("refunded by cancelling the order");
	}

	@Test
	void should_throwBusinessRule_when_paymentAlreadyCompleted() {
		// Arrange
		Order order = order(100L, OrderStatus.CONFIRMED);
		order.getPayment().setPaymentStatus(PaymentStatus.COMPLETED);
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updatePaymentStatus(100L, PaymentStatus.FAILED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("is already COMPLETED");
	}

	@Test
	void should_throwBusinessRule_when_payingForCancelledOrder() {
		// Arrange
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order(100L, OrderStatus.CANCELLED)));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updatePaymentStatus(100L, PaymentStatus.COMPLETED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("is cancelled");
	}

	@Test
	void should_throwResourceNotFound_when_orderHasNoPayment() {
		// Arrange
		Order order = order(100L, OrderStatus.PENDING);
		order.setPayment(null);
		when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

		// Act & Assert
		assertThatThrownBy(() -> orderService.updatePaymentStatus(100L, PaymentStatus.COMPLETED))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Payment not found with orderId: 100");
	}

	// --- fixtures ---

	private Product product(Long id, String sku, String price, int stock) {
		return Product.builder()
				.id(id)
				.name("Product " + sku)
				.price(new BigDecimal(price))
				.sku(sku)
				.stockQuantity(stock)
				.category(category)
				.build();
	}

	private Order order(Long id, OrderStatus status) {
		Order order = Order.builder()
				.id(id)
				.orderNumber("ORD-" + id)
				.customer(customer)
				.status(status)
				.totalAmount(new BigDecimal("20.00"))
				.shippingAddress("1 Main St")
				.build();
		order.addItem(OrderItem.builder()
				.product(product(10L, "SKU-BOOK", "10.00", 5))
				.quantity(2)
				.unitPrice(new BigDecimal("10.00"))
				.build());
		order.setPayment(Payment.builder()
				.paymentMethod(PaymentMethod.CREDIT_CARD)
				.amount(new BigDecimal("20.00"))
				.build());
		return order;
	}

	private static OrderItemRequest item(Long productId, int quantity) {
		return OrderItemRequest.builder().productId(productId).quantity(quantity).build();
	}

	private static OrderRequest orderRequest(List<OrderItemRequest> items, String shippingAddress) {
		return OrderRequest.builder()
				.customerId(1L)
				.items(items)
				.paymentMethod(PaymentMethod.CREDIT_CARD)
				.shippingAddress(shippingAddress)
				.build();
	}
}
