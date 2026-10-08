package com.ecommerce.service;

import com.ecommerce.dto.CategoryRequest;
import com.ecommerce.dto.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;

	public List<CategoryResponse> findAll() {
		return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
	}

	public CategoryResponse findById(Long id) {
		return CategoryResponse.from(getCategory(id));
	}

	@Transactional
	public CategoryResponse create(CategoryRequest request) {
		checkUnique(request, null);
		Category category = Category.builder()
				.name(request.getName())
				.slug(request.getSlug())
				.description(request.getDescription())
				.build();
		return CategoryResponse.from(categoryRepository.save(category));
	}

	@Transactional
	public CategoryResponse update(Long id, CategoryRequest request) {
		Category category = getCategory(id);
		checkUnique(request, category);
		category.setName(request.getName());
		category.setSlug(request.getSlug());
		category.setDescription(request.getDescription());
		return CategoryResponse.from(categoryRepository.saveAndFlush(category));
	}

	@Transactional
	public void delete(Long id) {
		Category category = getCategory(id);
		if (productRepository.existsByCategoryId(id)) {
			throw new BusinessRuleException("Category %d still has products and cannot be deleted".formatted(id));
		}
		categoryRepository.delete(category);
	}

	Category getCategory(Long id) {
		return categoryRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
	}

	private void checkUnique(CategoryRequest request, Category existing) {
		boolean nameChanged = existing == null || !Objects.equals(existing.getName(), request.getName());
		if (nameChanged && categoryRepository.existsByName(request.getName())) {
			throw new DuplicateResourceException("Category", "name", request.getName());
		}
		boolean slugChanged = existing == null || !Objects.equals(existing.getSlug(), request.getSlug());
		if (slugChanged && categoryRepository.existsBySlug(request.getSlug())) {
			throw new DuplicateResourceException("Category", "slug", request.getSlug());
		}
	}
}
