package com.asknehru.interviewsimulator.syllabus;

import com.asknehru.interviewsimulator.syllabus.Topic;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findByUserOrderByCreatedAtDesc(User user);
    Optional<Topic> findByUserAndName(User user, String name);
}
