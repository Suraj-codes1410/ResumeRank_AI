-- V5__create_candidates_and_scores_tables.sql

CREATE TABLE candidates (
    id UUID PRIMARY KEY,
    job_posting_id UUID NOT NULL,
    name TEXT,
    email TEXT,
    resume_file_url TEXT NOT NULL,
    resume_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    parse_error TEXT,
    pipeline_status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_candidates_job_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT chk_resume_status CHECK (resume_status IN ('PENDING', 'PARSING', 'SCORED', 'FAILED')),
    CONSTRAINT chk_pipeline_status CHECK (pipeline_status IN ('NEW', 'REVIEWING', 'SHORTLISTED', 'REJECTED'))
);

CREATE TABLE candidate_scores (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL UNIQUE,
    overall_score INTEGER NOT NULL,
    skills_score INTEGER NOT NULL,
    experience_score INTEGER NOT NULL,
    seniority_score INTEGER NOT NULL,
    matched_skills TEXT[] NOT NULL DEFAULT '{}',
    missing_skills TEXT[] NOT NULL DEFAULT '{}',
    years_experience_detected NUMERIC,
    summary TEXT,
    scored_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_candidate_scores_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    CONSTRAINT chk_overall_score CHECK (overall_score >= 0 AND overall_score <= 100),
    CONSTRAINT chk_skills_score CHECK (skills_score >= 0 AND skills_score <= 100),
    CONSTRAINT chk_experience_score CHECK (experience_score >= 0 AND experience_score <= 100),
    CONSTRAINT chk_seniority_score CHECK (seniority_score >= 0 AND seniority_score <= 100)
);

-- Trigger to auto-set updated_at on candidates updates
CREATE OR REPLACE FUNCTION set_candidates_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_candidates_updated_at
BEFORE UPDATE ON candidates
FOR EACH ROW
EXECUTE FUNCTION set_candidates_updated_at();

-- Index on candidates(job_posting_id, resume_status) for high-performance polling
CREATE INDEX idx_candidates_job_posting_status ON candidates(job_posting_id, resume_status);
