package com.asknehru.interviewsimulator.roadmap;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    List<Roadmap> findAllByOrderByCreatedAtDescIdDesc();
    Optional<Roadmap> findByRouterLink(String routerLink);
}
