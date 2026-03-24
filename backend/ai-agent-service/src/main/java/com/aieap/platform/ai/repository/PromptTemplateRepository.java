package com.aieap.platform.ai.repository;

import com.aieap.platform.ai.domain.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, UUID> {
    
    Optional<PromptTemplate> findByName(String name);
    
    @Query("SELECT p FROM PromptTemplate p WHERE p.isActive = true ORDER BY p.category, p.name")
    List<PromptTemplate> findAllActive();
    
    @Query("SELECT p FROM PromptTemplate p WHERE p.category = :category AND p.isActive = true")
    List<PromptTemplate> findByCategory(@Param("category") String category);
}
