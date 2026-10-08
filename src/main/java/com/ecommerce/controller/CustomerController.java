package com.ecommerce.controller;

import com.ecommerce.dto.CustomerRequest;
import com.ecommerce.dto.CustomerResponse;
import com.ecommerce.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;

	@GetMapping
	public Page<CustomerResponse> findAll(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return customerService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public CustomerResponse findById(@PathVariable Long id) {
		return customerService.findById(id);
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
		CustomerResponse created = customerService.create(request);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri()).body(created);
	}

	@PutMapping("/{id}")
	public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
		return customerService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		customerService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
