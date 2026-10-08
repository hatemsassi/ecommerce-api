package com.ecommerce.dto;

import com.ecommerce.entity.Payment;
import com.ecommerce.entity.enums.PaymentMethod;
import com.ecommerce.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

	private Long id;
	private PaymentMethod paymentMethod;
	private PaymentStatus paymentStatus;
	private BigDecimal amount;
	private LocalDateTime createdAt;

	public static PaymentResponse from(Payment payment) {
		return PaymentResponse.builder()
				.id(payment.getId())
				.paymentMethod(payment.getPaymentMethod())
				.paymentStatus(payment.getPaymentStatus())
				.amount(payment.getAmount())
				.createdAt(payment.getCreatedAt())
				.build();
	}
}
