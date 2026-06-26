package com.asknehru.interviewsimulator.test.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManipulationCategoryRepository extends JpaRepository<ManipulationCategory, Long> {
}
