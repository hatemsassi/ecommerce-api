package com.ecommerce.controller;

import com.ecommerce.config.WebConfig;
import com.ecommerce.dto.ProductRequest;
import com.ecommerce.dto.ProductResponse;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(ProductController.class)
@Import(WebConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ProductControllerTest {

	private static final String URL = "/api/v1/products";

	private static final String VALID_JSON = """
			{"name":"Clean Code","description":"A handbook","price":39.99,"sku":"BK-001",\
			"stockQuantity":10,"active":true,"categoryId":5}""";

	private final MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	// ---------- GET /products ----------

	@Test
	void should_return200WithPage_when_listingActiveProducts() throws Exception {
		// Arrange
		when(productService.findActive(isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response())));

		// Act
		ResultActions result = mockMvc.perform(get(URL));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].sku").value("BK-001"))
				.andExpect(jsonPath("$.content[0].categoryName").value("Books"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void should_return200FilteredPage_when_categoryIdGiven() throws Exception {
		// Arrange
		when(productService.findActive(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response())));

		// Act
		ResultActions result = mockMvc.perform(get(URL).param("categoryId", "5"));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].categoryId").value(5))
				.andExpect(jsonPath("$.page.totalElements").value(1));
		verify(productService).findActive(eq(5L), any(Pageable.class));
	}

	@Test
	void should_return400_when_categoryIdParamIsNotANumber() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(get(URL).param("categoryId", "abc"));

		// Assert
		result.andExpect(status().isBadRequest());
		verifyNoInteractions(productService);
	}

	// ---------- GET /products/{id} ----------

	@Test
	void should_return200WithProduct_when_productExists() throws Exception {
		// Arrange
		when(productService.findById(1L)).thenReturn(response());

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/1"));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Clean Code"))
				.andExpect(jsonPath("$.price").value(39.99))
				.andExpect(jsonPath("$.stockQuantity").value(10))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void should_return404_when_productDoesNotExist() throws Exception {
		// Arrange
		when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", "id", 99L));

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Product not found with id: 99"))
				.andExpect(jsonPath("$.path").value("/api/v1/products/99"));
	}

	@Test
	void should_return400_when_idIsNotANumber() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(get(URL + "/abc"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request: check the JSON body and parameter types"));
		verifyNoInteractions(productService);
	}

	// ---------- POST /products ----------

	@Test
	void should_return201WithProductAndLocation_when_requestIsValid() throws Exception {
		// Arrange
		when(productService.create(any(ProductRequest.class))).thenReturn(response());

		// Act
		ResultActions result = postProduct(VALID_JSON);

		// Assert
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/products/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Clean Code"))
				.andExpect(jsonPath("$.sku").value("BK-001"))
				.andExpect(jsonPath("$.categoryId").value(5));
	}

	@Test
	void should_return400_when_nameIsBlank() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("\"Clean Code\"", "\"  \""));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_nameExceeds200Characters() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("Clean Code", "a".repeat(201)));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_priceIsMissing() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("\"price\":39.99,", ""));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_priceIsBelowMinimum() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("39.99", "0.00"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_priceHasMoreThanTwoDecimals() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("39.99", "39.999"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_priceHasMoreThanEightIntegerDigits() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("39.99", "123456789.00"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_skuIsBlank() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("\"BK-001\"", "\"\""));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("sku"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_skuExceeds100Characters() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("BK-001", "S".repeat(101)));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("sku"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_stockQuantityIsMissing() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("\"stockQuantity\":10,", ""));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("stockQuantity"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_stockQuantityIsNegative() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace("\"stockQuantity\":10", "\"stockQuantity\":-1"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("stockQuantity"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_categoryIdIsMissing() throws Exception {
		// Act
		ResultActions result = postProduct(VALID_JSON.replace(",\"categoryId\":5", ""));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400ListingAllFieldErrors_when_bodyIsEmpty() throws Exception {
		// Act
		ResultActions result = postProduct("{}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(5))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'price')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'sku')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'stockQuantity')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'categoryId')]").exists());
		verifyNoInteractions(productService);
	}

	@Test
	void should_return400_when_jsonIsMalformed() throws Exception {
		// Act
		ResultActions result = postProduct("{\"name\":\"Clean Code\",");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request: check the JSON body and parameter types"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return409_when_skuAlreadyExists() throws Exception {
		// Arrange
		when(productService.create(any(ProductRequest.class)))
				.thenThrow(new DuplicateResourceException("Product", "sku", "BK-001"));

		// Act
		ResultActions result = postProduct(VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Product already exists with sku: BK-001"));
	}

	@Test
	void should_return404_when_creatingWithUnknownCategory() throws Exception {
		// Arrange
		when(productService.create(any(ProductRequest.class)))
				.thenThrow(new ResourceNotFoundException("Category", "id", 5L));

		// Act
		ResultActions result = postProduct(VALID_JSON);

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Category not found with id: 5"));
	}

	// ---------- PUT /products/{id} ----------

	@Test
	void should_return200WithUpdatedProduct_when_updateIsValid() throws Exception {
		// Arrange
		ProductResponse updated = response();
		updated.setName("Clean Code 2nd Ed.");
		updated.setPrice(new BigDecimal("44.99"));
		when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(updated);

		// Act
		ResultActions result = putProduct(1L, VALID_JSON
				.replace("Clean Code", "Clean Code 2nd Ed.").replace("39.99", "44.99"));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Clean Code 2nd Ed."))
				.andExpect(jsonPath("$.price").value(44.99));
	}

	@Test
	void should_return400_when_updateBodyIsInvalid() throws Exception {
		// Act
		ResultActions result = putProduct(1L, VALID_JSON.replace("\"stockQuantity\":10", "\"stockQuantity\":-5"));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("stockQuantity"));
		verifyNoInteractions(productService);
	}

	@Test
	void should_return404_when_updatingUnknownProduct() throws Exception {
		// Arrange
		when(productService.update(eq(99L), any(ProductRequest.class)))
				.thenThrow(new ResourceNotFoundException("Product", "id", 99L));

		// Act
		ResultActions result = putProduct(99L, VALID_JSON);

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Product not found with id: 99"));
	}

	@Test
	void should_return409_when_updatingToExistingSku() throws Exception {
		// Arrange
		when(productService.update(eq(1L), any(ProductRequest.class)))
				.thenThrow(new DuplicateResourceException("Product", "sku", "BK-001"));

		// Act
		ResultActions result = putProduct(1L, VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Product already exists with sku: BK-001"));
	}

	// ---------- DELETE /products/{id} ----------

	@Test
	void should_return204_when_deletingExistingProduct() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/1"));

		// Assert
		result.andExpect(status().isNoContent());
		verify(productService).delete(1L);
	}

	@Test
	void should_return404_when_deletingUnknownProduct() throws Exception {
		// Arrange
		doThrow(new ResourceNotFoundException("Product", "id", 99L)).when(productService).delete(99L);

		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Product not found with id: 99"));
	}

	private ResultActions postProduct(String json) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions putProduct(Long id, String json) throws Exception {
		return mockMvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static ProductResponse response() {
		return ProductResponse.builder()
				.id(1L)
				.name("Clean Code")
				.description("A handbook")
				.price(new BigDecimal("39.99"))
				.sku("BK-001")
				.stockQuantity(10)
				.active(true)
				.categoryId(5L)
				.categoryName("Books")
				.build();
	}
}
