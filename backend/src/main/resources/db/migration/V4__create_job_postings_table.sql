-- V4__create_job_postings_table.sql

CREATE TABLE job_postings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    required_skills TEXT[] DEFAULT '{}',
    nice_to_have_skills TEXT[] DEFAULT '{}',
    min_years_experience INTEGER,
    seniority_level VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_postings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_seniority_level CHECK (seniority_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

-- Trigger to auto-set updated_at on updates
CREATE OR REPLACE FUNCTION set_job_postings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_job_postings_updated_at
BEFORE UPDATE ON job_postings
FOR EACH ROW
EXECUTE FUNCTION set_job_postings_updated_at();

-- Database index on user_id for rapid filtering by recruiter owner
CREATE INDEX idx_job_postings_user_id ON job_postings(user_id);
