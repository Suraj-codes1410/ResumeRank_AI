package com.resumerank.backend.support;

import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared utility to generate mock entities for test cases.
 */
public class TestEntityGenerator {

    public static User createMockUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hashed_password_123");
        user.setEmailVerified(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    public static JobPosting createMockJobPosting(String title, User user) {
        JobPosting posting = new JobPosting();
        posting.setId(UUID.randomUUID());
        posting.setTitle(title);
        posting.setDescription("Mock description for " + title);
        posting.setRequiredSkills(new String[]{"Java", "Spring Boot"});
        posting.setNiceToHaveSkills(new String[]{"Docker", "Kubernetes"});
        posting.setMinYearsExperience(5);
        posting.setSeniorityLevel(com.resumerank.backend.entity.SeniorityLevel.SENIOR);
        posting.setStatus(com.resumerank.backend.entity.JobPostingStatus.ACTIVE);
        posting.setCreatedAt(OffsetDateTime.now());
        posting.setUpdatedAt(OffsetDateTime.now());
        return posting;
    }

    public static Candidate createMockCandidate(JobPosting posting, String name, String email) {
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setJobPosting(posting);
        candidate.setName(name);
        candidate.setEmail(email);
        candidate.setResumeFileUrl("http://cloudinary.com/resumes/mock.pdf");
        candidate.setResumeStatus(com.resumerank.backend.entity.ResumeStatus.PENDING);
        candidate.setPipelineStatus(com.resumerank.backend.entity.PipelineStatus.NEW);
        candidate.setCreatedAt(OffsetDateTime.now());
        candidate.setUpdatedAt(OffsetDateTime.now());
        return candidate;
    }
}
