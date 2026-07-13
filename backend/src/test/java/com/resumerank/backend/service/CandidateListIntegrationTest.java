package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateListResponse;
import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.CandidateScore;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.exception.ResourceNotFoundException;
import com.resumerank.backend.repository.CandidateRepository;
import com.resumerank.backend.repository.CandidateScoreRepository;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SpringBootTest
@Transactional
class CandidateListIntegrationTest {

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateScoreRepository candidateScoreRepository;

    private User recruiterA;
    private User recruiterB;
    private JobPosting postingA;

    @BeforeEach
    void setUp() {
        // Clear repositories to start fresh
        candidateScoreRepository.deleteAllInBatch();
        candidateRepository.deleteAllInBatch();
        jobPostingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // 1. Create Recruiter A
        recruiterA = new User();
        recruiterA.setEmail("recruiterA@example.com");
        recruiterA.setPasswordHash("hashedpassword123");
        recruiterA = userRepository.saveAndFlush(recruiterA);

        // 2. Create Recruiter B
        recruiterB = new User();
        recruiterB.setEmail("recruiterB@example.com");
        recruiterB.setPasswordHash("hashedpassword456");
        recruiterB = userRepository.saveAndFlush(recruiterB);

        // 3. Create JobPosting for Recruiter A
        postingA = new JobPosting();
        postingA.setUser(recruiterA);
        postingA.setTitle("Java Backend Engineer");
        postingA.setDescription("Java developer with spring experience");
        postingA.setStatus(JobPostingStatus.ACTIVE);
        postingA.setRequiredSkills(new String[]{"Java", "Spring"});
        postingA.setNiceToHaveSkills(new String[]{"Postgres"});
        postingA.setMinYearsExperience(3);
        postingA = jobPostingRepository.saveAndFlush(postingA);
    }

    @Test
    void getCandidatesList_SortingScoreDesc_PutsScoredAboveUnscored() {
        // 1. Create older unscored candidate (Created 2 days ago)
        Candidate unscoredOld = new Candidate();
        unscoredOld.setJobPosting(postingA);
        unscoredOld.setResumeFileUrl("http://cloudinary.com/unscored-old.pdf");
        unscoredOld.setResumeStatus(ResumeStatus.PENDING);
        unscoredOld.setPipelineStatus(PipelineStatus.NEW);
        unscoredOld.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
        unscoredOld = candidateRepository.saveAndFlush(unscoredOld);

        // 2. Create newer scored candidate (Created 1 day ago)
        Candidate scoredNew = new Candidate();
        scoredNew.setJobPosting(postingA);
        scoredNew.setResumeFileUrl("http://cloudinary.com/scored-new.pdf");
        scoredNew.setResumeStatus(ResumeStatus.SCORED);
        scoredNew.setPipelineStatus(PipelineStatus.NEW);
        scoredNew.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        scoredNew = candidateRepository.saveAndFlush(scoredNew);

        CandidateScore score = new CandidateScore();
        score.setCandidate(scoredNew);
        score.setOverallScore(85);
        score.setSkillsScore(85);
        score.setExperienceScore(85);
        score.setSeniorityScore(85);
        score.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(score);

        // Fetch list sorted by score_desc
        CandidateListResponse response = candidateService.getCandidatesList(
                recruiterA.getId(),
                postingA.getId(),
                "score_desc",
                null,
                null,
                null,
                null,
                null,
                10
        );

        // Assert scoredNew is first, and unscoredOld is second (Nulls last)
        Assertions.assertEquals(2, response.getItems().size());
        Assertions.assertEquals(scoredNew.getId(), response.getItems().get(0).getId());
        Assertions.assertEquals(unscoredOld.getId(), response.getItems().get(1).getId());
    }

    @Test
    void getCandidatesList_MinScoreFilter_ExcludesLowerScoredAndUnscored() {
        // 1. Candidate A: score = 85
        Candidate candidateA = new Candidate();
        candidateA.setJobPosting(postingA);
        candidateA.setResumeFileUrl("http://cloudinary.com/candidateA.pdf");
        candidateA.setResumeStatus(ResumeStatus.SCORED);
        candidateA = candidateRepository.saveAndFlush(candidateA);

        CandidateScore scoreA = new CandidateScore();
        scoreA.setCandidate(candidateA);
        scoreA.setOverallScore(85);
        scoreA.setSkillsScore(85);
        scoreA.setExperienceScore(85);
        scoreA.setSeniorityScore(85);
        scoreA.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(scoreA);

        // 2. Candidate B: score = 75
        Candidate candidateB = new Candidate();
        candidateB.setJobPosting(postingA);
        candidateB.setResumeFileUrl("http://cloudinary.com/candidateB.pdf");
        candidateB.setResumeStatus(ResumeStatus.SCORED);
        candidateB = candidateRepository.saveAndFlush(candidateB);

        CandidateScore scoreB = new CandidateScore();
        scoreB.setCandidate(candidateB);
        scoreB.setOverallScore(75);
        scoreB.setSkillsScore(75);
        scoreB.setExperienceScore(75);
        scoreB.setSeniorityScore(75);
        scoreB.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(scoreB);

        // 3. Candidate C: unscored (null)
        Candidate candidateC = new Candidate();
        candidateC.setJobPosting(postingA);
        candidateC.setResumeFileUrl("http://cloudinary.com/candidateC.pdf");
        candidateC.setResumeStatus(ResumeStatus.PENDING);
        candidateC = candidateRepository.saveAndFlush(candidateC);

        // Query with minScore = 80
        CandidateListResponse response = candidateService.getCandidatesList(
                recruiterA.getId(),
                postingA.getId(),
                "score_desc",
                80,
                null,
                null,
                null,
                null,
                10
        );

        // Assert only Candidate A is returned
        Assertions.assertEquals(1, response.getItems().size());
        Assertions.assertEquals(candidateA.getId(), response.getItems().get(0).getId());
    }

    @Test
    void getCandidatesList_UnauthorizedRecruiter_ThrowsResourceNotFoundException() {
        // Attempt to fetch candidates list for postingA using recruiterB (who does not own postingA)
        Assertions.assertThrows(ResourceNotFoundException.class, () -> {
            candidateService.getCandidatesList(
                    recruiterB.getId(),
                    postingA.getId(),
                    "score_desc",
                    null,
                    null,
                    null,
                    null,
                    null,
                    10
            );
        });
    }
}
