package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.AspirationChecklist;
import com.asknehru.interviewsimulator.model.UserAspiration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AspirationChecklistRepository extends JpaRepository<AspirationChecklist, Long> {
    Optional<AspirationChecklist> findByAspiration(UserAspiration aspiration);
}
