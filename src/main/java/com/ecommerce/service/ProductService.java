package com.ecommerce.service;

import com.ecommerce.dto.ProductRequest;
import com.ecommerce.dto.ProductResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final CategoryService categoryService;

	public Page<ProductResponse> findActive(Long categoryId, Pageable pageable) {
		Page<Product> products = categoryId == null
				? productRepository.findByActiveTrue(pageable)
				: productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
		return products.map(ProductResponse::from);
	}

	public ProductResponse findById(Long id) {
		return ProductResponse.from(getProduct(id));
	}

	@Transactional
	public ProductResponse create(ProductRequest request) {
		if (productRepository.existsBySku(request.getSku())) {
			throw new DuplicateResourceException("Product", "sku", request.getSku());
		}
		Product product = Product.builder()
				.name(request.getName())
				.description(request.getDescription())
				.price(request.getPrice())
				.sku(request.getSku())
				.stockQuantity(request.getStockQuantity())
				.active(request.getActive() == null || request.getActive())
				.category(categoryService.getCategory(request.getCategoryId()))
				.build();
		return ProductResponse.from(productRepository.save(product));
	}

	@Transactional
	public ProductResponse update(Long id, ProductRequest request) {
		Product product = getProduct(id);
		if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
			throw new DuplicateResourceException("Product", "sku", request.getSku());
		}
		product.setName(request.getName());
		product.setDescription(request.getDescription());
		product.setPrice(request.getPrice());
		product.setSku(request.getSku());
		product.setStockQuantity(request.getStockQuantity());
		if (request.getActive() != null) {
			product.setActive(request.getActive());
		}
		product.setCategory(categoryService.getCategory(request.getCategoryId()));
		return ProductResponse.from(productRepository.saveAndFlush(product));
	}

	// Soft delete: past order items still reference the product.
	@Transactional
	public void delete(Long id) {
		getProduct(id).setActive(false);
	}

	private Product getProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
	}
}
