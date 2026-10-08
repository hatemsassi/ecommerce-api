package com.ecommerce.controller;

import com.ecommerce.config.WebConfig;
import com.ecommerce.dto.CustomerRequest;
import com.ecommerce.dto.CustomerResponse;
import com.ecommerce.exception.BusinessRuleException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.CustomerService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
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

@WebMvcTest(CustomerController.class)
@Import(WebConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class CustomerControllerTest {

	private static final String URL = "/api/v1/customers";
	private static final String VALID_JSON =
			"{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane@example.com\",\"phone\":\"+33600000000\",\"address\":\"1 Main St\"}";

	private final MockMvc mockMvc;

	@MockitoBean
	private CustomerService customerService;

	// ---------- GET /customers ----------

	@Test
	void should_return200WithPage_when_listingCustomers() throws Exception {
		// Arrange
		when(customerService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response())));

		// Act
		ResultActions result = mockMvc.perform(get(URL));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].email").value("jane@example.com"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	// ---------- GET /customers/{id} ----------

	@Test
	void should_return200WithCustomer_when_customerExists() throws Exception {
		// Arrange
		when(customerService.findById(1L)).thenReturn(response());

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/1"));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.firstName").value("Jane"))
				.andExpect(jsonPath("$.lastName").value("Doe"))
				.andExpect(jsonPath("$.email").value("jane@example.com"));
	}

	@Test
	void should_return404_when_customerDoesNotExist() throws Exception {
		// Arrange
		when(customerService.findById(99L)).thenThrow(new ResourceNotFoundException("Customer", "id", 99L));

		// Act
		ResultActions result = mockMvc.perform(get(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Customer not found with id: 99"));
	}

	@Test
	void should_return400_when_idIsNotANumber() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(get(URL + "/abc"));

		// Assert
		result.andExpect(status().isBadRequest());
		verifyNoInteractions(customerService);
	}

	// ---------- POST /customers ----------

	@Test
	void should_return201WithCustomerAndLocation_when_requestIsValid() throws Exception {
		// Arrange
		when(customerService.create(any(CustomerRequest.class))).thenReturn(response());

		// Act
		ResultActions result = postCustomer(VALID_JSON);

		// Assert
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/customers/1")))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.firstName").value("Jane"))
				.andExpect(jsonPath("$.email").value("jane@example.com"));
	}

	@Test
	void should_return400_when_firstNameIsBlank() throws Exception {
		// Act
		ResultActions result = postCustomer(json("  ", "Doe", "jane@example.com", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_firstNameExceeds100Characters() throws Exception {
		// Act
		ResultActions result = postCustomer(json("a".repeat(101), "Doe", "jane@example.com", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_lastNameIsBlank() throws Exception {
		// Act
		ResultActions result = postCustomer(json("Jane", "", "jane@example.com", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("lastName"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_lastNameExceeds100Characters() throws Exception {
		// Act
		ResultActions result = postCustomer(json("Jane", "d".repeat(101), "jane@example.com", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("lastName"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_emailIsBlank() throws Exception {
		// Act
		ResultActions result = postCustomer(json("Jane", "Doe", "", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_emailIsMalformed() throws Exception {
		// Act
		ResultActions result = postCustomer(json("Jane", "Doe", "not-an-email", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_emailExceeds255Characters() throws Exception {
		// Arrange
		String label = "d".repeat(60);
		String longEmail = "jane@" + String.join(".", label, label, label, label, label, "com");

		// Act
		ResultActions result = postCustomer(json("Jane", "Doe", longEmail, null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists());
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_phoneExceeds20Characters() throws Exception {
		// Act
		ResultActions result = postCustomer(json("Jane", "Doe", "jane@example.com", "1".repeat(21)));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400ListingAllFieldErrors_when_severalFieldsInvalid() throws Exception {
		// Act
		ResultActions result = postCustomer("{\"phone\":\"" + "1".repeat(21) + "\"}");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(4))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'firstName')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'lastName')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')]").exists());
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return400_when_jsonIsMalformed() throws Exception {
		// Act
		ResultActions result = postCustomer("{\"firstName\":\"Jane\",");

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request: check the JSON body and parameter types"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return409_when_emailAlreadyExistsOnCreate() throws Exception {
		// Arrange
		when(customerService.create(any(CustomerRequest.class)))
				.thenThrow(new DuplicateResourceException("Customer", "email", "jane@example.com"));

		// Act
		ResultActions result = postCustomer(VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Customer already exists with email: jane@example.com"));
	}

	// ---------- PUT /customers/{id} ----------

	@Test
	void should_return200WithUpdatedCustomer_when_updateIsValid() throws Exception {
		// Arrange
		CustomerResponse updated = response();
		updated.setFirstName("Janet");
		when(customerService.update(eq(1L), any(CustomerRequest.class))).thenReturn(updated);

		// Act
		ResultActions result = putCustomer(1L, json("Janet", "Doe", "jane@example.com", null));

		// Assert
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.firstName").value("Janet"));
	}

	@Test
	void should_return400_when_updateEmailIsMalformed() throws Exception {
		// Act
		ResultActions result = putCustomer(1L, json("Jane", "Doe", "bad-email", null));

		// Assert
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
		verifyNoInteractions(customerService);
	}

	@Test
	void should_return404_when_updatingUnknownCustomer() throws Exception {
		// Arrange
		when(customerService.update(eq(99L), any(CustomerRequest.class)))
				.thenThrow(new ResourceNotFoundException("Customer", "id", 99L));

		// Act
		ResultActions result = putCustomer(99L, VALID_JSON);

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Customer not found with id: 99"));
	}

	@Test
	void should_return409_when_updateEmailBelongsToAnotherCustomer() throws Exception {
		// Arrange
		when(customerService.update(eq(1L), any(CustomerRequest.class)))
				.thenThrow(new DuplicateResourceException("Customer", "email", "jane@example.com"));

		// Act
		ResultActions result = putCustomer(1L, VALID_JSON);

		// Assert
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Customer already exists with email: jane@example.com"));
	}

	// ---------- DELETE /customers/{id} ----------

	@Test
	void should_return204_when_customerDeleted() throws Exception {
		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/1"));

		// Assert
		result.andExpect(status().isNoContent());
		verify(customerService).delete(1L);
	}

	@Test
	void should_return404_when_deletingUnknownCustomer() throws Exception {
		// Arrange
		doThrow(new ResourceNotFoundException("Customer", "id", 99L)).when(customerService).delete(99L);

		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/99"));

		// Assert
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Customer not found with id: 99"));
	}

	@Test
	void should_return422_when_customerHasOrders() throws Exception {
		// Arrange
		doThrow(new BusinessRuleException("Customer 1 has orders and cannot be deleted"))
				.when(customerService).delete(1L);

		// Act
		ResultActions result = mockMvc.perform(delete(URL + "/1"));

		// Assert
		result.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value("Customer 1 has orders and cannot be deleted"));
	}

	private ResultActions postCustomer(String json) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions putCustomer(Long id, String json) throws Exception {
		return mockMvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String json(String firstName, String lastName, String email, String phone) {
		return "{\"firstName\":\"%s\",\"lastName\":\"%s\",\"email\":\"%s\"%s}".formatted(
				firstName, lastName, email, phone == null ? "" : ",\"phone\":\"" + phone + "\"");
	}

	private static CustomerResponse response() {
		return CustomerResponse.builder()
				.id(1L)
				.firstName("Jane")
				.lastName("Doe")
				.email("jane@example.com")
				.phone("+33600000000")
				.address("1 Main St")
				.createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
				.build();
	}
}
