package com.ecommerce.controller;

import com.ecommerce.config.WebConfig;
import com.ecommerce.dto.CategoryRequest;
import com.ecommerce.dto.CategoryResponse;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(WebConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CategoryControllerTest {

	private static final String URL = "/api/v1/categories";
	private static final String VALID_JSON = "{\"name\":\"Books\",\"slug\":\"books\",\"description\":\"All books\"}";

	private final MockMvc mockMvc;

	@MockitoBean
	private CategoryService categoryService;

	// GET /api/v1/categories

	@Test
	void should_return200WithAllCategories_when_listingCategories() throws Exception {
		// Arrange
		when(categoryService.findAll()).thenReturn(List.of(response()));

		// Act
		ResultActions result = mockMvc.perform(get(URL));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("Books"))
				.andExpect(jsonPath("$[0].slug").value("books"));
	}

	// GET /api/v1/categories/{id}

	@Test
	void should_return200WithCategory_when_categoryExists() throws Exception {
		// Arrange
		when(categoryService.findById(1L)).thenReturn(response());

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/1"));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Books"))
				.andExpect(jsonPath("$.slug").value("books"))
				.andExpect(jsonPath("$.description").value("All books"));
	}

	@Test
	void should_return404_when_categoryDoesNotExist() throws Exception {
		// Arrange
		when(categoryService.findById(99L)).thenThrow(new ResourceNotFoundException("Category", "id", 99L));

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Category not found with id: 99"));
	}

	@Test
	void should_return400_when_idIsNotANumber() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(get(URL + "/abc"));

		// Assert
		result.andExpect(status().isBadRequest());
		verifyNoInteractions(categoryService);
	}

	// POST /api/v1/categories

	@Test
	void should_return201WithCategory_when_requestIsValid() throws Exception {
		// Arrange
		when(categoryService.create(any(CategoryRequest.class))).thenReturn(response());

		// Act
		ResultActions result = postCategory(VALID_JSON);

		// Assert
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/categories/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Books"))
				.andExpect(jsonPath("$.slug").value("books"));
	}

	@Test
	void should_return400_when_nameIsBlank() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"  \",\"slug\":\"books\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400_when_nameExceeds100Characters() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"" + "a".repeat(101) + "\",\"slug\":\"books\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400_when_slugIsMissing() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"Books\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400_when_slugExceeds100Characters() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"Books\",\"slug\":\"" + "a".repeat(101) + "\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400_when_slugHasInvalidFormat() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"Books\",\"slug\":\"Sci Fi_Books\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("slug"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("must be lowercase letters, digits and hyphens"));
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400ListingAllFieldErrors_when_nameAndSlugMissing() throws Exception {
		// Act
		ResultActions result = postCategory("{\"description\":\"No name or slug\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(2))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'slug')]").exists());
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return400_when_jsonIsMalformed() throws Exception {
		// Act
		ResultActions result = postCategory("{\"name\":\"Books\",");

		// Assert
		result.andExpect(status().isBadRequest());
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return409_when_categoryNameAlreadyExists() throws Exception {
		// Arrange
		when(categoryService.create(any(CategoryRequest.class)))
				.thenThrow(new DuplicateResourceException("Category", "name", "Books"));

		// Act
		ResultActions result = postCategory(VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Category already exists with name: Books"));
	}

	@Test
	void should_return409_when_categorySlugAlreadyExists() throws Exception {
		// Arrange
		when(categoryService.create(any(CategoryRequest.class)))
				.thenThrow(new DuplicateResourceException("Category", "slug", "books"));

		// Act
		ResultActions result = postCategory(VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Category already exists with slug: books"));
	}

	// PUT /api/v1/categories/{id}

	@Test
	void should_return200WithUpdatedCategory_when_updateIsValid() throws Exception {
		// Arrange
		CategoryResponse updated = CategoryResponse.builder()
				.id(1L)
				.name("Novels")
				.slug("novels")
				.description("Fiction")
				.build();
		when(categoryService.update(eq(1L), any(CategoryRequest.class))).thenReturn(updated);

		// Act
		ResultActions result = putCategory(1L, "{\"name\":\"Novels\",\"slug\":\"novels\",\"description\":\"Fiction\"}");

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Novels"))
				.andExpect(jsonPath("$.slug").value("novels"))
				.andExpect(jsonPath("$.description").value("Fiction"));
	}

	@Test
	void should_return400_when_updateRequestIsInvalid() throws Exception {
		// Act
		ResultActions result = putCategory(1L, "{\"name\":\"\",\"slug\":\"Bad Slug\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'slug')]").exists());
		verifyNoInteractions(categoryService);
	}

	@Test
	void should_return404_when_updatingUnknownCategory() throws Exception {
		// Arrange
		when(categoryService.update(eq(99L), any(CategoryRequest.class)))
				.thenThrow(new ResourceNotFoundException("Category", "id", 99L));

		// Act
		ResultActions result = putCategory(99L, VALID_JSON);

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Category not found with id: 99"));
	}

	@Test
	void should_return409_when_updatingToExistingSlug() throws Exception {
		// Arrange
		when(categoryService.update(eq(1L), any(CategoryRequest.class)))
				.thenThrow(new DuplicateResourceException("Category", "slug", "books"));

		// Act
		ResultActions result = putCategory(1L, VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Category already exists with slug: books"));
	}

	// DELETE /api/v1/categories/{id}

	@Test
	void should_return204_when_categoryIsDeleted() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/1"));

		// Assert
		result.andExpect(status().isNoContent());
		verify(categoryService).delete(1L);
	}

	@Test
	void should_return404_when_deletingUnknownCategory() throws Exception {
		// Arrange
		doThrow(new ResourceNotFoundException("Category", "id", 99L)).when(categoryService).delete(99L);

		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Category not found with id: 99"));
	}

	@Test
	void should_return422_when_deletingCategoryWithProducts() throws Exception {
		// Arrange
		doThrow(new BusinessRuleException("Category 1 still has products and cannot be deleted"))
				.when(categoryService).delete(1L);

		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/1"));

		// Assert
		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value("Category 1 still has products and cannot be deleted"));
	}

	private ResultActions postCategory(String json) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions putCategory(Long id, String json) throws Exception {
		return mockMvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static CategoryResponse response() {
		return CategoryResponse.builder()
				.id(1L)
				.name("Books")
				.slug("books")
				.description("All books")
				.build();
	}
}
