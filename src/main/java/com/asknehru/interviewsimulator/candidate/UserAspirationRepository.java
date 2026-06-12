package com.asknehru.interviewsimulator.candidate;

import com.asknehru.interviewsimulator.candidate.UserAspiration;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAspirationRepository extends JpaRepository<UserAspiration, Long> {
    List<UserAspiration> findByUserOrderByUpdatedAtDesc(User user);
}
