package com.resumerank.backend.repository;

import com.resumerank.backend.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    @Query("SELECT c FROM Candidate c JOIN FETCH c.jobPosting WHERE c.id = :id")
    Optional<Candidate> findByIdWithJobPosting(@Param("id") UUID id);
}
