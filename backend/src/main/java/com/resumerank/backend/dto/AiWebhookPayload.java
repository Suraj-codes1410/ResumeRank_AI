package com.resumerank.backend.dto;

import java.util.List;

public class AiWebhookPayload {
    private String candidateId;
    private boolean success;
    private String error;
    private ScoreDto score;

    public AiWebhookPayload() {
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ScoreDto getScore() {
        return score;
    }

    public void setScore(ScoreDto score) {
        this.score = score;
    }

    public static class ScoreDto {
        private Integer overallScore;
        private Integer skillsScore;
        private Integer experienceScore;
        private Integer seniorityScore;
        private List<String> matchedSkills;
        private List<String> missingSkills;
        private Double yearsExperienceDetected;
        private String summary;

        public ScoreDto() {
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

        public List<String> getMatchedSkills() {
            return matchedSkills;
        }

        public void setMatchedSkills(List<String> matchedSkills) {
            this.matchedSkills = matchedSkills;
        }

        public List<String> getMissingSkills() {
            return missingSkills;
        }

        public void setMissingSkills(List<String> missingSkills) {
            this.missingSkills = missingSkills;
        }

        public Double getYearsExperienceDetected() {
            return yearsExperienceDetected;
        }

        public void setYearsExperienceDetected(Double yearsExperienceDetected) {
            this.yearsExperienceDetected = yearsExperienceDetected;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }
}
