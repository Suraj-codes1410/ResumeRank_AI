-- V7__create_candidate_status_log_table.sql

CREATE TABLE candidate_status_log (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    changed_by UUID NOT NULL,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_log_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_status_log_user FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_log_from_status CHECK (from_status IN ('NEW', 'REVIEWING', 'SHORTLISTED', 'REJECTED')),
    CONSTRAINT chk_log_to_status CHECK (to_status IN ('NEW', 'REVIEWING', 'SHORTLISTED', 'REJECTED'))
);

CREATE INDEX idx_candidate_status_log_candidate ON candidate_status_log(candidate_id);
