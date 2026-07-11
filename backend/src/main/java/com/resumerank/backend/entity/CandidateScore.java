package com.resumerank.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidate_scores")
public class CandidateScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "skills_score", nullable = false)
    private Integer skillsScore;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "experience_score", nullable = false)
    private Integer experienceScore;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "seniority_score", nullable = false)
    private Integer seniorityScore;

    @NotNull
    @Column(name = "matched_skills", columnDefinition = "text[]", nullable = false)
    private String[] matchedSkills = new String[0];

    @NotNull
    @Column(name = "missing_skills", columnDefinition = "text[]", nullable = false)
    private String[] missingSkills = new String[0];

    @Column(name = "years_experience_detected")
    private BigDecimal yearsExperienceDetected;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @NotNull
    @Column(name = "scored_at", nullable = false)
    private OffsetDateTime scoredAt;

    public CandidateScore() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public Integer getSkillsScore() {
        return skillsScore;
    }

    public void setSkillsScore(Integer skillsScore) {
        this.skillsScore = skillsScore;
    }

    public Integer getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(Integer experienceScore) {
        this.experienceScore = experienceScore;
    }

    public Integer getSeniorityScore() {
        return seniorityScore;
    }

    public void setSeniorityScore(Integer seniorityScore) {
        this.seniorityScore = seniorityScore;
    }

    public String[] getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(String[] matchedSkills) {
        this.matchedSkills = matchedSkills;
    }

    public String[] getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(String[] missingSkills) {
        this.missingSkills = missingSkills;
    }

    public BigDecimal getYearsExperienceDetected() {
        return yearsExperienceDetected;
    }

    public void setYearsExperienceDetected(BigDecimal yearsExperienceDetected) {
        this.yearsExperienceDetected = yearsExperienceDetected;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public OffsetDateTime getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(OffsetDateTime scoredAt) {
        this.scoredAt = scoredAt;
    }
}
