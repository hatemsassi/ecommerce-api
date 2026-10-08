package com.ecommerce.entity;

import com.ecommerce.entity.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

	private static final String ORDER_NUMBER_PREFIX = "ORD-";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, unique = true, updatable = false, length = 20)
	private String orderNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private OrderStatus status = OrderStatus.PENDING;

	@Builder.Default
	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount = BigDecimal.ZERO;

	@Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
	private String shippingAddress;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Builder.Default
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	@OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private Payment payment;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	void generateOrderNumber() {
		if (orderNumber == null) {
			orderNumber = ORDER_NUMBER_PREFIX
					+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		}
	}

	// Both sides of each relationship must be set, or the cascade inserts a null order_id.
	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	public void removeItem(OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}

	public void setPayment(Payment payment) {
		if (this.payment != null) {
			this.payment.setOrder(null);
		}
		this.payment = payment;
		if (payment != null) {
			payment.setOrder(this);
		}
	}
}
