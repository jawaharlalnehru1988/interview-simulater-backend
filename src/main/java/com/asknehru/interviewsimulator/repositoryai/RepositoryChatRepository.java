package com.asknehru.interviewsimulator.repositoryai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RepositoryChatRepository extends JpaRepository<RepositoryChat, Long> {
    List<RepositoryChat> findByRepositoryAnalysisIdOrderByCreatedAtAsc(Long repositoryAnalysisId);
    
    @Transactional
    void deleteByRepositoryAnalysisId(Long repositoryAnalysisId);
}
