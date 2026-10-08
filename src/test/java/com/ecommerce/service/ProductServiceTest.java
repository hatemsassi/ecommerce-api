package com.ecommerce.service;

import com.ecommerce.dto.ProductRequest;
import com.ecommerce.dto.ProductResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	private static final Pageable PAGEABLE = PageRequest.of(0, 20);

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CategoryService categoryService;

	@InjectMocks
	private ProductService productService;

	private final Category category = Category.builder().id(1L).name("Books").slug("books").build();

	@Test
	void should_returnAllActiveProducts_when_categoryIdIsNull() {
		// Arrange
		when(productRepository.findByActiveTrue(PAGEABLE)).thenReturn(new PageImpl<>(List.of(product(10L, "SKU-1"))));

		// Act
		Page<ProductResponse> result = productService.findActive(null, PAGEABLE);

		// Assert
		assertThat(result.getContent()).extracting(ProductResponse::getSku).containsExactly("SKU-1");
	}

	@Test
	void should_returnActiveProductsOfCategory_when_categoryIdProvided() {
		// Arrange
		when(productRepository.findByCategoryIdAndActiveTrue(1L, PAGEABLE))
				.thenReturn(new PageImpl<>(List.of(product(10L, "SKU-1"))));

		// Act
		Page<ProductResponse> result = productService.findActive(1L, PAGEABLE);

		// Assert
		assertThat(result.getContent()).extracting(ProductResponse::getCategoryId).containsExactly(1L);
		verify(productRepository, never()).findByActiveTrue(any());
	}

	@Test
	void should_returnProduct_when_idExists() {
		// Arrange
		when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L, "SKU-1")));

		// Act
		ProductResponse result = productService.findById(10L);

		// Assert
		assertThat(result.getId()).isEqualTo(10L);
		assertThat(result.getCategoryName()).isEqualTo("Books");
	}

	@Test
	void should_throwResourceNotFound_when_idDoesNotExist() {
		// Arrange
		when(productRepository.findById(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> productService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Product not found with id: 99");
	}

	@Test
	void should_createActiveProduct_when_skuIsUniqueAndActiveOmitted() {
		// Arrange
		ProductRequest request = request("SKU-1", null);
		when(productRepository.existsBySku("SKU-1")).thenReturn(false);
		when(categoryService.getCategory(1L)).thenReturn(category);
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product saved = invocation.getArgument(0);
			saved.setId(10L);
			return saved;
		});

		// Act
		ProductResponse result = productService.create(request);

		// Assert
		assertThat(result.getId()).isEqualTo(10L);
		assertThat(result.getSku()).isEqualTo("SKU-1");
		assertThat(result.getActive()).isTrue();
		assertThat(result.getStockQuantity()).isEqualTo(5);
		assertThat(result.getCategoryId()).isEqualTo(1L);
	}

	@Test
	void should_throwDuplicateResource_when_createWithExistingSku() {
		// Arrange
		ProductRequest request = request("SKU-1", true);
		when(productRepository.existsBySku("SKU-1")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> productService.create(request))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("Product already exists with sku: SKU-1");
		verify(productRepository, never()).save(any());
		verify(categoryService, never()).getCategory(anyLong());
	}

	@Test
	void should_throwResourceNotFound_when_createWithUnknownCategory() {
		// Arrange
		ProductRequest request = request("SKU-1", true);
		when(productRepository.existsBySku("SKU-1")).thenReturn(false);
		when(categoryService.getCategory(1L)).thenThrow(new ResourceNotFoundException("Category", "id", 1L));

		// Act & Assert
		assertThatThrownBy(() -> productService.create(request))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Category not found");
		verify(productRepository, never()).save(any());
	}

	@Test
	void should_updateProduct_when_skuUnchanged() {
		// Arrange
		Product existing = product(10L, "SKU-1");
		ProductRequest request = request("SKU-1", false);
		request.setPrice(new BigDecimal("12.50"));
		when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
		when(categoryService.getCategory(1L)).thenReturn(category);
		when(productRepository.saveAndFlush(existing)).thenReturn(existing);

		// Act
		ProductResponse result = productService.update(10L, request);

		// Assert
		assertThat(result.getPrice()).isEqualByComparingTo("12.50");
		assertThat(result.getActive()).isFalse();
		verify(productRepository, never()).existsBySku(any());
	}

	@Test
	void should_throwDuplicateResource_when_updateToExistingSku() {
		// Arrange
		when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L, "SKU-1")));
		when(productRepository.existsBySku("SKU-2")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> productService.update(10L, request("SKU-2", true)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("sku: SKU-2");
		verify(productRepository, never()).saveAndFlush(any());
	}

	@Test
	void should_deactivateProduct_when_deleteCalled() {
		// Arrange
		Product existing = product(10L, "SKU-1");
		when(productRepository.findById(10L)).thenReturn(Optional.of(existing));

		// Act
		productService.delete(10L);

		// Assert
		assertThat(existing.getActive()).isFalse();
		verify(productRepository, never()).delete(any());
	}

	private Product product(Long id, String sku) {
		return Product.builder()
				.id(id)
				.name("Product " + sku)
				.price(new BigDecimal("9.99"))
				.sku(sku)
				.stockQuantity(5)
				.category(category)
				.build();
	}

	private static ProductRequest request(String sku, Boolean active) {
		return ProductRequest.builder()
				.name("Product " + sku)
				.price(new BigDecimal("9.99"))
				.sku(sku)
				.stockQuantity(5)
				.active(active)
				.categoryId(1L)
				.build();
	}
}
