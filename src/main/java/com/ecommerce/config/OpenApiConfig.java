package com.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI: /swagger-ui.html, OpenAPI JSON: /v3/api-docs
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI ecommerceOpenApi() {
		return new OpenAPI().info(new Info()
				.title("E-commerce API")
				.description("REST API for categories, products, customers and orders")
				.version("v1"));
	}
}
