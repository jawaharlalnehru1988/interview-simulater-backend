package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.UserAspiration;
import com.asknehru.interviewsimulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAspirationRepository extends JpaRepository<UserAspiration, Long> {
    List<UserAspiration> findByUserOrderByUpdatedAtDesc(User user);
}
