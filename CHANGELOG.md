# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-22

### Added
- Multi-stage Dockerfile using Maven alpine and Eclipse Temurin JRE alpine for secure, minimal production container hosting.
- Custom `.dockerignore` to restrict build context size and exclude IDE, git, and local configurations.
- JaCoCo Maven plugin configuration integrated with the verify lifecycle to automatically compile coverage statistics.
- Direct environment configuration guide mapping backend endpoints and AI service webhooks.

### Changed
- Refactored GitHub Actions workflow to combine Integration Tests and coverage reporting into a single unified step, eliminating duplicate execution of the Maven verify lifecycle and reducing CI build times by over 5 minutes.
- Separated Unit Tests (`mvn test`) and Integration Tests (`mvn verify`) using distinct Surefire and Failsafe plugin phases.

### Fixed
- Resolved native array deserialization exceptions (`invalid stream header`) during candidate searches by implementing explicit `addScalar` mappings for Postgres native arrays inside `CandidateService.java`.

---

## [0.9.0] - 2026-07-15

### Added
- Relational candidate status logging system to track and audit candidate pipeline status transitions.
- Interactive status filters and pagination support on the main recruiter workspace grid.
- Dynamic CSV exporter utility that formats native array attributes into structured, Excel-compliant cells.

### Changed
- Configured production database parameters to support SSL mode connection requirements for secure remote hosting on Render.
- Optimized JPA entity loading parameters to prevent N+1 queries during parent-child candidate score resolution.

---

## [0.8.0] - 2026-07-11

### Added
- Python FastAPI microservice architecture responsible for asynchronous resume text parsing and analysis.
- LangChain Structured Output scoring chain using Google Gemini models (via OpenRouter) to grade candidates on Skills, Experience, and Seniority alignment.
- Secure inter-service HTTP authentication flow enforcing custom `X-Internal-Token` headers between Spring Boot and FastAPI.
- Robust parsing for PDF documents (`pypdf` stream parsing) and Word documents (`python-docx`).

### Fixed
- Implemented background thread retry policies with exponential backoffs inside the Spring Boot client to manage network hiccups during FastAPI service calls.

---

## [0.5.0] - 2026-07-09

### Added
- Spring Security filter chain enforcing stateless JWT token validation.
- Secure token-refresh flow utilizing HTTP-Only, Secure, and SameSite HTTP cookies.
- Cloudinary client-side upload proxy helper that generates secure signatures, preventing plain API secrets from leaking into client bundles.
- Integration with Resend Email API for sending recruiter verification codes and password reset links.
- Thymeleaf HTML templates for outbound recruiter communications.

---

## [0.1.0] - 2026-07-05

### Added
- Initial modular project layout comprising `backend`, `frontend`, and `aiservice` folders.
- Relational schema tables (Users, Job Postings, Candidates, Candidate Scores) managed via Flyway migration scripts (V1 to V5).
- Testcontainers configuration for automatic Postgres container initialization during test runs.
- Next.js 15 UI boilerplate using TailwindCSS and Axios HTTP clients.

[1.0.0]: https://github.com/Suraj-codes1410/ResumeRank_AI/releases/tag/v1.0.0
[0.9.0]: https://github.com/Suraj-codes1410/ResumeRank_AI/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/Suraj-codes1410/ResumeRank_AI/compare/v0.5.0...v0.8.0
[0.5.0]: https://github.com/Suraj-codes1410/ResumeRank_AI/compare/v0.1.0...v0.5.0
[0.1.0]: https://github.com/Suraj-codes1410/ResumeRank_AI/releases/tag/v0.1.0
