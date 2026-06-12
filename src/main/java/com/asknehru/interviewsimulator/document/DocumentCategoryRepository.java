package com.asknehru.interviewsimulator.document;

import com.asknehru.interviewsimulator.document.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    java.util.Optional<DocumentCategory> findByName(String name);
}
