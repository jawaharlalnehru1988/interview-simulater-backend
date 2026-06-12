package com.asknehru.interviewsimulator.document;

import com.asknehru.interviewsimulator.document.Document;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserOrderByCreatedAtDesc(User user);
}
