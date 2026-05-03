package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.PersonalCoachAttempt;
import com.asknehru.interviewsimulator.model.PersonalCoachSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonalCoachAttemptRepository extends JpaRepository<PersonalCoachAttempt, Long> {
    List<PersonalCoachAttempt> findBySessionOrderByCreatedAtDesc(PersonalCoachSession session);
}
