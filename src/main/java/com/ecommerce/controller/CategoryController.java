package com.ecommerce.controller;

import com.ecommerce.dto.CategoryRequest;
import com.ecommerce.dto.CategoryResponse;
import com.ecommerce.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping
	public List<CategoryResponse> findAll() {
		return categoryService.findAll();
	}

	@GetMapping("/{id}")
	public CategoryResponse findById(@PathVariable Long id) {
		return categoryService.findById(id);
	}

	@PostMapping
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
		CategoryResponse created = categoryService.create(request);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri()).body(created);
	}

	@PutMapping("/{id}")
	public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
		return categoryService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		categoryService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
