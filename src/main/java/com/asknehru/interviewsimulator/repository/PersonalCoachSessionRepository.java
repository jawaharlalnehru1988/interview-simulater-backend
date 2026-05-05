package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.PersonalCoachSession;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PersonalCoachSessionRepository extends JpaRepository<PersonalCoachSession, Long> {
    List<PersonalCoachSession> findByUserOrderByUpdatedAtDesc(User user);
    Optional<PersonalCoachSession> findFirstByUserAndTopicOrderByIdDesc(User user, String topic);
}
