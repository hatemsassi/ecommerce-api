package com.ecommerce.dto;

import com.ecommerce.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

	@NotNull
	private Long customerId;

	@NotEmpty
	private List<@Valid @NotNull OrderItemRequest> items;

	@NotNull
	private PaymentMethod paymentMethod;

	// Falls back to the customer's address when omitted
	private String shippingAddress;

	private String notes;
}
