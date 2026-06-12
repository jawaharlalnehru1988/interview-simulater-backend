package com.asknehru.interviewsimulator.candidate;

import com.asknehru.interviewsimulator.candidate.AspirationChecklist;
import com.asknehru.interviewsimulator.candidate.UserAspiration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AspirationChecklistRepository extends JpaRepository<AspirationChecklist, Long> {
    Optional<AspirationChecklist> findByAspiration(UserAspiration aspiration);
}
