package com.asknehru.interviewsimulator.roadmap;

import com.asknehru.roadmap.model.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository("simulatorRoadmapRepository")
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    List<Roadmap> findAllByOrderByCreatedAtDescIdDesc();
    Optional<Roadmap> findByRouterLink(String routerLink);
}
