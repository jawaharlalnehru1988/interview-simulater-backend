package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    java.util.Optional<DocumentCategory> findByName(String name);
}
