package com.resumerank.backend.repository;

import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.JobPostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    @Query("SELECT j FROM JobPosting j WHERE j.user.id = :userId " +
           "AND (:status IS NULL OR j.status = :status)")
    Page<JobPosting> findByUserIdAndOptionalStatus(
        @Param("userId") UUID userId,
        @Param("status") JobPostingStatus status,
        Pageable pageable
    );
}
