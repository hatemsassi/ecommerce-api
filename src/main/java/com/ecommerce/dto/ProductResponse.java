package com.ecommerce.dto;

import com.ecommerce.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

	private Long id;
	private String name;
	private String description;
	private BigDecimal price;
	private String sku;
	private Integer stockQuantity;
	private Boolean active;
	private Long categoryId;
	private String categoryName;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static ProductResponse from(Product product) {
		return ProductResponse.builder()
				.id(product.getId())
				.name(product.getName())
				.description(product.getDescription())
				.price(product.getPrice())
				.sku(product.getSku())
				.stockQuantity(product.getStockQuantity())
				.active(product.getActive())
				.categoryId(product.getCategory().getId())
				.categoryName(product.getCategory().getName())
				.createdAt(product.getCreatedAt())
				.updatedAt(product.getUpdatedAt())
				.build();
	}
}
