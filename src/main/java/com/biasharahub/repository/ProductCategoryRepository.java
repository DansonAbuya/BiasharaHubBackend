package com.biasharahub.repository;

import com.biasharahub.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findAllByOrderByDisplayOrderAscNameAsc();
}
