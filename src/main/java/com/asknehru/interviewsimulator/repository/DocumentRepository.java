package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Document;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserOrderByCreatedAtDesc(User user);
}
