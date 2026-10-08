package com.ecommerce.service;

import com.ecommerce.dto.CustomerRequest;
import com.ecommerce.dto.CustomerResponse;
import com.ecommerce.entity.Customer;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CustomerRepository;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

	private final CustomerRepository customerRepository;
	private final OrderRepository orderRepository;

	public Page<CustomerResponse> findAll(Pageable pageable) {
		return customerRepository.findAll(pageable).map(CustomerResponse::from);
	}

	public CustomerResponse findById(Long id) {
		return CustomerResponse.from(getCustomer(id));
	}

	@Transactional
	public CustomerResponse create(CustomerRequest request) {
		if (customerRepository.existsByEmail(request.getEmail())) {
			throw new DuplicateResourceException("Customer", "email", request.getEmail());
		}
		Customer customer = Customer.builder()
				.firstName(request.getFirstName())
				.lastName(request.getLastName())
				.email(request.getEmail())
				.phone(request.getPhone())
				.address(request.getAddress())
				.build();
		return CustomerResponse.from(customerRepository.save(customer));
	}

	@Transactional
	public CustomerResponse update(Long id, CustomerRequest request) {
		Customer customer = getCustomer(id);
		if (!customer.getEmail().equalsIgnoreCase(request.getEmail())
				&& customerRepository.existsByEmail(request.getEmail())) {
			throw new DuplicateResourceException("Customer", "email", request.getEmail());
		}
		customer.setFirstName(request.getFirstName());
		customer.setLastName(request.getLastName());
		customer.setEmail(request.getEmail());
		customer.setPhone(request.getPhone());
		customer.setAddress(request.getAddress());
		return CustomerResponse.from(customer);
	}

	@Transactional
	public void delete(Long id) {
		Customer customer = getCustomer(id);
		if (orderRepository.existsByCustomerId(id)) {
			throw new BusinessRuleException("Customer %d has orders and cannot be deleted".formatted(id));
		}
		customerRepository.delete(customer);
	}

	Customer getCustomer(Long id) {
		return customerRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
	}
}
