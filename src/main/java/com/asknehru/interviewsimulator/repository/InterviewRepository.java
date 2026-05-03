package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.Interview;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserOrderByUpdatedAtDesc(User user);
}
