package com.biasharahub.repository;

import com.biasharahub.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {

    List<ServiceCategory> findAllByOrderByDisplayOrderAscNameAsc();
}
