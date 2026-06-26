package com.asknehru.interviewsimulator.codingtrainer.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplexityCategoryRepository extends JpaRepository<ComplexityCategory, Long> {
    Optional<ComplexityCategory> findByName(String name);
}
