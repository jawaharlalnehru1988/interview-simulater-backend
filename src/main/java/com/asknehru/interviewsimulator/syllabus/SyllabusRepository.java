package com.asknehru.interviewsimulator.syllabus;

import com.asknehru.interviewsimulator.syllabus.Syllabus;
import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {
    List<Syllabus> findByUserOrderByUpdatedAtDesc(User user);
    Optional<Syllabus> findByIdAndUser(Long id, User user);
}
