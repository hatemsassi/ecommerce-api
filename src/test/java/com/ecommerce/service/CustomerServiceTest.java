package com.ecommerce.service;

import com.ecommerce.dto.CustomerRequest;
import com.ecommerce.dto.CustomerResponse;
import com.ecommerce.entity.Customer;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CustomerRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private OrderRepository orderRepository;

	@InjectMocks
	private CustomerService customerService;

	@Test
	void should_returnPageOfCustomers_when_findAllCalled() {
		// Arrange
		Pageable pageable = PageRequest.of(0, 10);
		when(customerRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(customer(1L, "jane@example.com"))));

		// Act
		Page<CustomerResponse> result = customerService.findAll(pageable);

		// Assert
		assertThat(result.getContent()).extracting(CustomerResponse::getEmail).containsExactly("jane@example.com");
	}

	@Test
	void should_returnCustomer_when_idExists() {
		// Arrange
		when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "jane@example.com")));

		// Act
		CustomerResponse result = customerService.findById(1L);

		// Assert
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getFirstName()).isEqualTo("Jane");
	}

	@Test
	void should_throwResourceNotFound_when_idDoesNotExist() {
		// Arrange
		when(customerRepository.findById(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> customerService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Customer not found with id: 99");
	}

	@Test
	void should_registerCustomer_when_emailIsUnique() {
		// Arrange
		CustomerRequest request = request("jane@example.com");
		when(customerRepository.existsByEmail("jane@example.com")).thenReturn(false);
		when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
			Customer saved = invocation.getArgument(0);
			saved.setId(1L);
			return saved;
		});

		// Act
		CustomerResponse result = customerService.create(request);

		// Assert
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getEmail()).isEqualTo("jane@example.com");
		assertThat(result.getAddress()).isEqualTo("1 Main St");
	}

	@Test
	void should_throwDuplicateResource_when_registerWithExistingEmail() {
		// Arrange
		CustomerRequest request = request("jane@example.com");
		when(customerRepository.existsByEmail("jane@example.com")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> customerService.create(request))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("Customer already exists with email: jane@example.com");
		verify(customerRepository, never()).save(any());
	}

	@Test
	void should_updateCustomer_when_emailOnlyChangesCase() {
		// Arrange
		Customer existing = customer(1L, "jane@example.com");
		CustomerRequest request = request("JANE@example.com");
		request.setFirstName("Janet");
		when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));

		// Act
		CustomerResponse result = customerService.update(1L, request);

		// Assert
		assertThat(result.getFirstName()).isEqualTo("Janet");
		assertThat(result.getEmail()).isEqualTo("JANE@example.com");
		verify(customerRepository, never()).existsByEmail(any());
	}

	@Test
	void should_throwDuplicateResource_when_updateToEmailOfAnotherCustomer() {
		// Arrange
		when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "jane@example.com")));
		when(customerRepository.existsByEmail("john@example.com")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> customerService.update(1L, request("john@example.com")))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("email: john@example.com");
	}

	@Test
	void should_deleteCustomer_when_customerHasNoOrders() {
		// Arrange
		Customer existing = customer(1L, "jane@example.com");
		when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(orderRepository.existsByCustomerId(1L)).thenReturn(false);

		// Act
		customerService.delete(1L);

		// Assert
		verify(customerRepository).delete(existing);
	}

	@Test
	void should_throwBusinessRule_when_deleteCustomerWithOrders() {
		// Arrange
		when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "jane@example.com")));
		when(orderRepository.existsByCustomerId(1L)).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> customerService.delete(1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("has orders");
		verify(customerRepository, never()).delete(any());
	}

	private static Customer customer(Long id, String email) {
		return Customer.builder()
				.id(id)
				.firstName("Jane")
				.lastName("Doe")
				.email(email)
				.address("1 Main St")
				.build();
	}

	private static CustomerRequest request(String email) {
		return CustomerRequest.builder()
				.firstName("Jane")
				.lastName("Doe")
				.email(email)
				.address("1 Main St")
				.build();
	}
}
