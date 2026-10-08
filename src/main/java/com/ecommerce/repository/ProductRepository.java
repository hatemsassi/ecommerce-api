package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findBySku(String sku);

	boolean existsBySku(String sku);

	boolean existsByCategoryId(Long categoryId);

	Page<Product> findByActiveTrue(Pageable pageable);

	Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

	// Row lock (SELECT ... FOR UPDATE) so concurrent orders can't oversell the same stock.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(Long id);

	@Modifying(flushAutomatically = true)
	@Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity where p.id = :id")
	int increaseStock(Long id, int quantity);
}
