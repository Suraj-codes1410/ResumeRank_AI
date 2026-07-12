package com.resumerank.backend.repository;

import com.resumerank.backend.entity.CandidateScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CandidateScoreRepository extends JpaRepository<CandidateScore, UUID> {
    
    boolean existsByCandidateId(UUID candidateId);
    
    java.util.Optional<CandidateScore> findByCandidateId(UUID candidateId);
}
