package com.resumerank.backend.service;

import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;

    public JobPostingService(JobPostingRepository jobPostingRepository, UserRepository userRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public JobPostingResponse createJobPosting(UUID userId, JobPostingCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        JobPosting jobPosting = new JobPosting();
        jobPosting.setUser(user);
        jobPosting.setTitle(request.title());
        jobPosting.setDescription(request.description());
        jobPosting.setRequiredSkills(request.requiredSkills() != null ? request.requiredSkills() : new String[0]);
        jobPosting.setNiceToHaveSkills(request.niceToHaveSkills() != null ? request.niceToHaveSkills() : new String[0]);
        jobPosting.setMinYearsExperience(request.minYearsExperience());
        jobPosting.setSeniorityLevel(request.seniorityLevel());
        jobPosting.setStatus(JobPostingStatus.ACTIVE);

        JobPosting saved = jobPostingRepository.saveAndFlush(jobPosting);

        return new JobPostingResponse(
            saved.getId(),
            saved.getUser().getId(),
            saved.getTitle(),
            saved.getDescription(),
            saved.getRequiredSkills(),
            saved.getNiceToHaveSkills(),
            saved.getMinYearsExperience(),
            saved.getSeniorityLevel(),
            saved.getStatus(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }
}
