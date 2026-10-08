package com.ecommerce.dto;

import com.ecommerce.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

	private Long id;
	private String firstName;
	private String lastName;
	private String email;
	private String phone;
	private String address;
	private LocalDateTime createdAt;

	public static CustomerResponse from(Customer customer) {
		return CustomerResponse.builder()
				.id(customer.getId())
				.firstName(customer.getFirstName())
				.lastName(customer.getLastName())
				.email(customer.getEmail())
				.phone(customer.getPhone())
				.address(customer.getAddress())
				.createdAt(customer.getCreatedAt())
				.build();
	}
}
