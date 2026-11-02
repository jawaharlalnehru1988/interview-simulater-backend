package com.asknehru.interviewsimulator.document;

import com.asknehru.interviewsimulator.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findByUserOrderByNameAsc(User user);
    List<FamilyMember> findAllByOrderByNameAsc();
    Optional<FamilyMember> findByNameAndUser(String name, User user);
    boolean existsByNameAndUser(String name, User user);
}

