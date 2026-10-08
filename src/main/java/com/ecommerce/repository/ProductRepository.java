package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findBySku(String sku);

	boolean existsBySku(String sku);

	Page<Product> findByActiveTrue(Pageable pageable);

	Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
}
