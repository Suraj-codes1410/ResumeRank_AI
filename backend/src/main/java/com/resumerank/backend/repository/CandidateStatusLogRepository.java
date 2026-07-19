package com.resumerank.backend.repository;

import com.resumerank.backend.entity.CandidateStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateStatusLogRepository extends JpaRepository<CandidateStatusLog, UUID> {

    @Query("SELECT l FROM CandidateStatusLog l JOIN FETCH l.changedBy WHERE l.candidate.id = :candidateId ORDER BY l.createdAt DESC")
    List<CandidateStatusLog> findByCandidateIdOrderByCreatedAtDesc(@Param("candidateId") UUID candidateId);

    default List<CandidateStatusLog> findAllByCandidateIdOrderByCreatedAtDesc(UUID candidateId) {
        return findByCandidateIdOrderByCreatedAtDesc(candidateId);
    }
}
