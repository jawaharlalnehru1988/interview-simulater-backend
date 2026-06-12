package com.asknehru.interviewsimulator.interview;

import com.asknehru.interviewsimulator.interview.Interview;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserOrderByUpdatedAtDesc(User user);
}
