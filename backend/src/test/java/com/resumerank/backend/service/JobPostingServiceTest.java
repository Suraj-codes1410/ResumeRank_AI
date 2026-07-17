package com.resumerank.backend.service;

import com.resumerank.backend.dto.JobPostingListResponse;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.SeniorityLevel;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class JobPostingServiceTest {

    private JobPostingRepository jobPostingRepository;
    private UserRepository userRepository;
    private JobPostingService jobPostingService;

    private UUID recruiterAId;
    private UUID recruiterBId;
    private User recruiterA;
    private User recruiterB;

    @BeforeEach
    void setUp() {
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        jobPostingService = new JobPostingService(jobPostingRepository, userRepository, mapper);

        recruiterAId = UUID.randomUUID();
        recruiterBId = UUID.randomUUID();

        recruiterA = new User();
        recruiterA.setId(recruiterAId);

        recruiterB = new User();
        recruiterB.setId(recruiterBId);
    }

    @Test
    void listJobPostings_OwnerIsolation() {
        // Recruiter A has 2 postings
        JobPosting post1 = createMockPosting(UUID.randomUUID(), recruiterA, "Post A1", JobPostingStatus.ACTIVE);
        JobPosting post2 = createMockPosting(UUID.randomUUID(), recruiterA, "Post A2", JobPostingStatus.ACTIVE);
        List<JobPosting> postingsA = List.of(post1, post2);

        Page<JobPosting> pageA = new PageImpl<>(postingsA);

        Mockito.when(jobPostingRepository.findByUserIdAndOptionalStatus(
                eq(recruiterAId), any(), any(Pageable.class)
        )).thenReturn(pageA);

        // Fetch postings for Recruiter A
        JobPostingListResponse response = jobPostingService.listJobPostings(recruiterAId, null, 0, 20);

        Assertions.assertEquals(2, response.items().size());
        Assertions.assertEquals(post1.getId(), response.items().get(0).id());
        Assertions.assertEquals(post2.getId(), response.items().get(1).id());
        
        // Assert we never return Recruiter B's postings
        for (var item : response.items()) {
            Assertions.assertEquals(recruiterAId, item.userId());
            Assertions.assertNotEquals(recruiterBId, item.userId());
        }
    }

    @Test
    void listJobPostings_Pagination() {
        // Create a list representing 20 returned items out of 25 total
        List<JobPosting> mockList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            mockList.add(createMockPosting(UUID.randomUUID(), recruiterA, "Post " + i, JobPostingStatus.ACTIVE));
        }

        Pageable expectedPageable = org.springframework.data.domain.PageRequest.of(0, 20);
        Page<JobPosting> mockPage = new PageImpl<>(mockList, expectedPageable, 25);

        Mockito.when(jobPostingRepository.findByUserIdAndOptionalStatus(
                eq(recruiterAId), any(), any(Pageable.class)
        )).thenReturn(mockPage);

        JobPostingListResponse response = jobPostingService.listJobPostings(recruiterAId, null, 0, 20);

        Assertions.assertEquals(20, response.items().size());
        Assertions.assertEquals(25, response.totalItems());
        Assertions.assertEquals(0, response.page());
    }

    @Test
    void listJobPostings_ClampsSize() {
        Page<JobPosting> emptyPage = new PageImpl<>(List.of());
        Mockito.when(jobPostingRepository.findByUserIdAndOptionalStatus(
                any(), any(), any(Pageable.class)
        )).thenReturn(emptyPage);

        // Call with size = 500
        jobPostingService.listJobPostings(recruiterAId, null, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(jobPostingRepository).findByUserIdAndOptionalStatus(
                any(), any(), pageableCaptor.capture()
        );

        Pageable capturedPageable = pageableCaptor.getValue();
        Assertions.assertEquals(100, capturedPageable.getPageSize()); // Clamped to 100!
    }

    @Test
    void listJobPostings_StatusFilter() {
        Page<JobPosting> emptyPage = new PageImpl<>(List.of());
        Mockito.when(jobPostingRepository.findByUserIdAndOptionalStatus(
                any(), any(), any(Pageable.class)
        )).thenReturn(emptyPage);

        // Request only ARCHIVED postings
        jobPostingService.listJobPostings(recruiterAId, JobPostingStatus.ARCHIVED, 0, 10);

        ArgumentCaptor<JobPostingStatus> statusCaptor = ArgumentCaptor.forClass(JobPostingStatus.class);
        Mockito.verify(jobPostingRepository).findByUserIdAndOptionalStatus(
                any(), statusCaptor.capture(), any(Pageable.class)
        );

        Assertions.assertEquals(JobPostingStatus.ARCHIVED, statusCaptor.getValue());
    }

    @Test
    void getJobPosting_Own_ReturnsPosting() {
        UUID postingId = UUID.randomUUID();
        JobPosting post = createMockPosting(postingId, recruiterA, "Engineer", JobPostingStatus.ACTIVE);
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.of(post));

        var response = jobPostingService.getJobPosting(recruiterAId, postingId);
        Assertions.assertEquals(postingId, response.id());
        Assertions.assertEquals("Engineer", response.title());
    }

    @Test
    void getJobPosting_NotOwn_ThrowsResourceNotFoundException() {
        UUID postingId = UUID.randomUUID();
        JobPosting post = createMockPosting(postingId, recruiterA, "Engineer", JobPostingStatus.ACTIVE);
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.of(post));

        Assertions.assertThrows(
                com.resumerank.backend.exception.ResourceNotFoundException.class,
                () -> jobPostingService.getJobPosting(recruiterBId, postingId)
        );
    }

    @Test
    void updateJobPosting_Own_UpdatesFieldsAndUpdatedAtChanges() throws Exception {
        UUID postingId = UUID.randomUUID();
        OffsetDateTime originalUpdatedAt = OffsetDateTime.now().minusHours(1);
        JobPosting post = createMockPosting(postingId, recruiterA, "Engineer", JobPostingStatus.ACTIVE);
        post.setUpdatedAt(originalUpdatedAt);
        
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.of(post));
        Mockito.when(jobPostingRepository.saveAndFlush(any(JobPosting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode patchNode = mapper.readTree("{\"title\":\"Lead Developer\",\"status\":\"archived\"}");

        var response = jobPostingService.updateJobPosting(recruiterAId, postingId, patchNode);

        Assertions.assertEquals("Lead Developer", response.title());
        Assertions.assertEquals(JobPostingStatus.ARCHIVED, response.status());
        Assertions.assertTrue(response.updatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void updateJobPosting_NotOwn_ThrowsResourceNotFoundExceptionAndDoesNotSave() throws Exception {
        UUID postingId = UUID.randomUUID();
        JobPosting post = createMockPosting(postingId, recruiterA, "Engineer", JobPostingStatus.ACTIVE);
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.of(post));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode patchNode = mapper.readTree("{\"title\":\"Lead Developer\"}");

        Assertions.assertThrows(
                com.resumerank.backend.exception.ResourceNotFoundException.class,
                () -> jobPostingService.updateJobPosting(recruiterBId, postingId, patchNode)
        );

        Mockito.verify(jobPostingRepository, Mockito.never()).saveAndFlush(any());
        Mockito.verify(jobPostingRepository, Mockito.never()).save(any());
    }

    @Test
    void createJobPosting_Success_SavesToRepository() {
        com.resumerank.backend.dto.JobPostingCreateRequest request = new com.resumerank.backend.dto.JobPostingCreateRequest(
                "Software Engineer", "Description", new String[]{"Java"}, new String[]{"Docker"}, 5, SeniorityLevel.SENIOR
        );

        Mockito.when(userRepository.findById(recruiterAId)).thenReturn(java.util.Optional.of(recruiterA));
        Mockito.when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var response = jobPostingService.createJobPosting(recruiterAId, request);

        Assertions.assertNotNull(response.id());
        Assertions.assertEquals("Software Engineer", response.title());
        Assertions.assertEquals(recruiterAId, response.userId());
        Mockito.verify(jobPostingRepository, Mockito.times(1)).save(any(JobPosting.class));
    }

    @Test
    void createJobPosting_UserNotFound_ThrowsResourceNotFoundException() {
        com.resumerank.backend.dto.JobPostingCreateRequest request = new com.resumerank.backend.dto.JobPostingCreateRequest(
                "Software Engineer", "Description", new String[0], new String[0], null, null
        );

        Mockito.when(userRepository.findById(recruiterAId)).thenReturn(java.util.Optional.empty());

        Assertions.assertThrows(
                com.resumerank.backend.exception.ResourceNotFoundException.class,
                () -> jobPostingService.createJobPosting(recruiterAId, request)
        );
        Mockito.verify(jobPostingRepository, Mockito.never()).save(any());
    }

    @Test
    void getJobPosting_NonexistentJob_ThrowsResourceNotFoundException() {
        UUID postingId = UUID.randomUUID();
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.empty());

        Assertions.assertThrows(
                com.resumerank.backend.exception.ResourceNotFoundException.class,
                () -> jobPostingService.getJobPosting(recruiterAId, postingId)
        );
    }

    @Test
    void updateJobPosting_NonexistentJob_ThrowsResourceNotFoundException() throws Exception {
        UUID postingId = UUID.randomUUID();
        Mockito.when(jobPostingRepository.findById(postingId)).thenReturn(java.util.Optional.empty());

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode patchNode = mapper.readTree("{\"title\":\"Lead Developer\"}");

        Assertions.assertThrows(
                com.resumerank.backend.exception.ResourceNotFoundException.class,
                () -> jobPostingService.updateJobPosting(recruiterAId, postingId, patchNode)
        );
        Mockito.verify(jobPostingRepository, Mockito.never()).saveAndFlush(any());
    }

    private JobPosting createMockPosting(UUID id, User user, String title, JobPostingStatus status) {
        JobPosting post = new JobPosting();
        post.setId(id);
        post.setUser(user);
        post.setTitle(title);
        post.setDescription("Description for " + title);
        post.setRequiredSkills(new String[]{"Java"});
        post.setNiceToHaveSkills(new String[]{"Spring"});
        post.setMinYearsExperience(3);
        post.setSeniorityLevel(SeniorityLevel.MID);
        post.setStatus(status);
        post.setCreatedAt(OffsetDateTime.now());
        post.setUpdatedAt(OffsetDateTime.now());
        return post;
    }
}
