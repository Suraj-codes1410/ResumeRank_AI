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

    @Query("SELECT c FROM Candidate c JOIN FETCH c.jobPosting LEFT JOIN FETCH c.candidateScore WHERE c.id = :id")
    Optional<Candidate> findByIdWithJobPostingAndScore(@Param("id") UUID id);

    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.candidateScore WHERE c.jobPosting.id = :jobPostingId")
    java.util.List<Candidate> findByJobPostingIdWithScore(@Param("jobPostingId") UUID jobPostingId);

    @Query("SELECT c FROM Candidate c WHERE c.jobPosting.id = :jobPostingId AND c.resumeHash = :resumeHash ORDER BY c.createdAt ASC")
    java.util.List<Candidate> findByJobPostingIdAndResumeHash(
            @Param("jobPostingId") UUID jobPostingId,
            @Param("resumeHash") String resumeHash
    );
}
