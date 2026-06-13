package com.asknehru.interviewsimulator.repositoryai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysis, Long> {
}
