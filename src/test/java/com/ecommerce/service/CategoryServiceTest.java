package com.ecommerce.service;

import com.ecommerce.dto.CategoryRequest;
import com.ecommerce.dto.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private ProductRepository productRepository;

	@InjectMocks
	private CategoryService categoryService;

	@Test
	void should_returnAllCategories_when_findAllCalled() {
		// Arrange
		when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "Books", "books"), category(2L, "Toys", "toys")));

		// Act
		List<CategoryResponse> result = categoryService.findAll();

		// Assert
		assertThat(result).extracting(CategoryResponse::getName).containsExactly("Books", "Toys");
	}

	@Test
	void should_returnCategory_when_idExists() {
		// Arrange
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Books", "books")));

		// Act
		CategoryResponse result = categoryService.findById(1L);

		// Assert
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getSlug()).isEqualTo("books");
	}

	@Test
	void should_throwResourceNotFound_when_idDoesNotExist() {
		// Arrange
		when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> categoryService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Category not found with id: 99");
	}

	@Test
	void should_createCategory_when_nameAndSlugAreUnique() {
		// Arrange
		CategoryRequest request = request("Books", "books");
		when(categoryRepository.existsByName("Books")).thenReturn(false);
		when(categoryRepository.existsBySlug("books")).thenReturn(false);
		when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
			Category saved = invocation.getArgument(0);
			saved.setId(1L);
			return saved;
		});

		// Act
		CategoryResponse result = categoryService.create(request);

		// Assert
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getName()).isEqualTo("Books");
		assertThat(result.getSlug()).isEqualTo("books");
		assertThat(result.getDescription()).isEqualTo("All about Books");
	}

	@Test
	void should_throwDuplicateResource_when_createWithExistingName() {
		// Arrange
		CategoryRequest request = request("Books", "books");
		when(categoryRepository.existsByName("Books")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> categoryService.create(request))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("Category already exists with name: Books");
		verify(categoryRepository, never()).save(any());
	}

	@Test
	void should_throwDuplicateResource_when_createWithExistingSlug() {
		// Arrange
		CategoryRequest request = request("Books", "books");
		when(categoryRepository.existsByName("Books")).thenReturn(false);
		when(categoryRepository.existsBySlug("books")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> categoryService.create(request))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("Category already exists with slug: books");
		verify(categoryRepository, never()).save(any());
	}

	@Test
	void should_updateCategory_when_nameAndSlugUnchanged() {
		// Arrange
		Category existing = category(1L, "Books", "books");
		CategoryRequest request = CategoryRequest.builder().name("Books").slug("books").description("Updated").build();
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);

		// Act
		CategoryResponse result = categoryService.update(1L, request);

		// Assert
		assertThat(result.getDescription()).isEqualTo("Updated");
		verify(categoryRepository, never()).existsByName(any());
		verify(categoryRepository, never()).existsBySlug(any());
	}

	@Test
	void should_throwDuplicateResource_when_updateToExistingName() {
		// Arrange
		Category existing = category(1L, "Books", "books");
		CategoryRequest request = request("Toys", "books");
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(categoryRepository.existsByName("Toys")).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> categoryService.update(1L, request))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("name: Toys");
		verify(categoryRepository, never()).saveAndFlush(any());
	}

	@Test
	void should_throwResourceNotFound_when_updateUnknownCategory() {
		// Arrange
		when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> categoryService.update(99L, request("Books", "books")))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void should_deleteCategory_when_categoryHasNoProducts() {
		// Arrange
		Category existing = category(1L, "Books", "books");
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(productRepository.existsByCategoryId(1L)).thenReturn(false);

		// Act
		categoryService.delete(1L);

		// Assert
		verify(categoryRepository).delete(existing);
	}

	@Test
	void should_throwBusinessRule_when_deleteCategoryWithProducts() {
		// Arrange
		when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Books", "books")));
		when(productRepository.existsByCategoryId(1L)).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> categoryService.delete(1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("still has products");
		verify(categoryRepository, never()).delete(any());
	}

	private static Category category(Long id, String name, String slug) {
		return Category.builder().id(id).name(name).slug(slug).description("All about " + name).build();
	}

	private static CategoryRequest request(String name, String slug) {
		return CategoryRequest.builder().name(name).slug(slug).description("All about " + name).build();
	}
}
