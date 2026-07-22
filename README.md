# ResumeRank AI

> An enterprise-grade, multi-service talent acquisition platform powered by LLM-driven resume extraction, scoring, and automated candidate ranking.

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://jdk.java.net/21/)
[![Spring Boot 3.3.1](https://img.shields.io/badge/Spring%20Boot-3.3.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Next.js 15](https://img.shields.io/badge/Next.js-15-000000?logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.111.0-009688?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-20.10-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD-2088FF?logo=githubactions&logoColor=white)](https://github.com/features/actions)
[![Flyway](https://img.shields.io/badge/Flyway-10-CC0000?logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.20-Orange?logo=testcontainers&logoColor=white)](https://testcontainers.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

ResumeRank AI streamlines the hiring process by parsing candidate resumes (PDFs and DOCXs), extracting structured details, comparing them against specific job descriptions, scoring them on key criteria (skills, experience, seniority), and ranking them in a unified recruiter dashboard.

---

## 📸 Screenshots

### Landing Page
![Landing Page](docs/screenshots/landing_page.png)

### Dashboard
![Dashboard](docs/screenshots/dashboard.png)

### Candidate Upload & Parsing
![Candidate Upload](docs/screenshots/candidate_upload.png)

### Candidate Scores & Analysis
![Candidate Scores](docs/screenshots/candidate_scores.png)

### Resume Ranking
![Resume Ranking](docs/screenshots/resume_ranking.png)

### Email Verification
![Email Verification](docs/screenshots/email_verification.png)

---

## 🔗 Demo Links

- **Live Web App**: [https://resume-rank-ai.vercel.app](https://resume-rank-ai.vercel.app)
- **Backend Service URL**: [https://resumerank-ai-zdww.onrender.com](https://resumerank-ai-zdww.onrender.com)
- **API Swagger Documentation**: [https://resumerank-ai-zdww.onrender.com/swagger-ui/index.html](https://resumerank-ai-zdww.onrender.com/swagger-ui/index.html)
- **AI Service OpenAPI Spec**: [https://resumerank-aiservice.onrender.com/docs](https://resumerank-aiservice.onrender.com/docs)
- **Product Demo Video**: [https://vimeo.com/resumerank-ai-demo](https://vimeo.com/resumerank-ai-demo)

---

## 🎯 Project Overview

In high-volume recruitment, reviewing hundreds of resumes manually is slow, error-prone, and biased. Recruiters spend hours scanning documents looking for specific skills, calculating years of experience, and classifying candidates' seniority levels.

**ResumeRank AI** solves this problem by automating the initial screening pipeline:
1. **Recruiters** create a job posting with specific required skills, nice-to-have skills, and target experience.
2. **Candidates** upload their resumes directly (PDF or DOCX format).
3. **The platform** uses custom AI models to automatically parse the files, extract textual data, evaluate skill matching, and grade them on multiple alignment categories.
4. **Candidates are ranked** automatically in real-time on a unified dashboard, enabling recruiters to identify top talent in seconds rather than days.

The platform is designed with a **highly scalable, multi-service, asynchronous microservices architecture** that handles background processing gracefully without locking user sessions.

---

## ✨ Features

### 🔐 Authentication & Security
- **Secure JWT Authentication**: JWT access and refresh token authentication pattern. Refresh tokens are secured via `HttpOnly`, `Secure`, and `SameSite` HTTP cookies to prevent XSS.
- **Email Verification**: Sign-up triggers automated email verification tokens sent via Resend API to validate recruiter email authenticity.
- **Recruiter Account Management**: Secure password hashing via `BCryptPasswordEncoder` and password reset flows with timed verification tokens.
- **Granular Ownership Control**: Access control guards protect REST resources, ensuring users can only manage candidate lists and job postings they own.

### 📄 Resume Management
- **Multi-Format Processing**: Direct upload and text extraction support for standard PDF and DOCX document formats.
- **BFF Proxy Signature Uploads**: Direct client-side uploads to Cloudinary storage via secure signature hashes fetched from the Backend-For-Frontend (BFF) endpoint to save server bandwidth.
- **De-duplication**: MD5 hashing (`resume_hash`) prevents processing duplicate resumes for the same candidate posting, reducing database clutter and API costs.

### 🤖 AI Processing & Scoring
- **Automated Text Extraction**: Python microservice parses PDF/DOCX files and extracts raw text securely.
- **Multi-Category Grading**: Deep scoring based on:
  - **Skills Alignment**: Keyword intersection and conceptual matching of skills.
  - **Experience Alignment**: Years of experience compared to target.
  - **Seniority Alignment**: Seniority classification (Junior, Mid, Senior, Lead).
  - **Overall Score**: Weighted aggregation of the categories.
- **Matched/Missing Skills Discovery**: Extracts which required skills are matched and lists missing requirements.
- **Suitability Summaries**: Generates a concise 1-2 sentence recruiter-facing suitability analysis for each candidate.

### 💼 Job & Candidate Management
- **Target Profiles**: Job postings contain title, description, required skills, nice-to-have skills, target experience, and seniority level.
- **Screening Dashboard**: Recruiters can create, search, filter, and sort candidates on a responsive grid dashboard.
- **Export CSV**: Secure native query CSV exporter handles parsing of native Postgres string arrays to write clean sheets.

### 🛠️ DevOps & Infrastructure
- **Test Separation**: Configured `maven-surefire-plugin` (unit tests) and `maven-failsafe-plugin` (integration tests) to optimize pipeline execution speed.
- **Testcontainers integration**: Runs real PostgreSQL docker instances during integration tests to guarantee 100% database compatibility.
- **Automatic Migration**: Database migrations executed automatically via Flyway during startup and test runs.
- **Dockerized Deployments**: Production-grade multi-stage Docker build configures the backend for Render container hosting.

---

## 💻 Tech Stack

| Layer | Technology | Version | Description |
| :--- | :--- | :--- | :--- |
| **Frontend** | React / Next.js | 15.x | Responsive user interface, App Router, tailwindcss |
| **Backend** | Spring Boot | 3.3.1 | Core API, security, async task execution, entity validation |
| **AI Service** | FastAPI | 0.111.0 | Fast Python processing, document parsing, LLM orchestration |
| **Database** | PostgreSQL | 16 | Relational storage, native arrays (`text[]`) |
| **Migrations** | Flyway | 10 | Strict schema migration control |
| **Security** | Spring Security | 6.x | JWT token auth, CORS filters |
| **Storage** | Cloudinary | - | Blob store for candidate resume uploads |
| **Emails** | Resend | - | Transactional emails (activation, password resets) |
| **DevOps** | Docker | - | Multi-stage image build containerization |
| **CI/CD** | GitHub Actions | - | Automated linting, test run, quality gate check |
| **Testing** | Testcontainers | 1.20 | Spawns clean Postgres Docker containers on integration runs |
| **Libraries** | LangChain / Uvicorn | - | Structured LLM parsing, FastAPI production server

---

## 🏗️ Architecture

### High-Level Service Architecture
The system consists of three independent nodes communicating over secure channels:

```mermaid
graph LR
    User([Recruiter / Candidate]) -->|HTTPS| Frontend[Next.js Frontend]
    Frontend -->|REST API Proxy| Backend[Spring Boot Backend]
    Backend -->|JDBC| DB[(PostgreSQL)]
    Backend -->|Direct Upload Signatures| Cloudinary[Cloudinary CDN]
    Backend -->|POST /internal/process-resume| AIService[FastAPI AI Service]
    Backend -->|SMTP REST| Resend[Resend Email API]
    AIService -->|LangChain HTTP| OpenRouter[OpenRouter / Gemini]
    AIService -->|POST /api/internal/ai-webhook| Backend
    
    style User fill:#d5e8d4,stroke:#82b366,stroke-width:2px
    style Frontend fill:#dae8fc,stroke:#6c8ebf,stroke-width:2px
    style Backend fill:#ffe6cc,stroke:#d79b00,stroke-width:2px
    style DB fill:#f5f5f5,stroke:#666666,stroke-width:2px
    style AIService fill:#e1d5e7,stroke:#9673a6,stroke-width:2px
```

### Backend Architecture
Inside the Spring Boot container, requests are handled via standard Layered Architecture pattern:

```mermaid
graph TD
    Controller[REST Controllers] -->|DTOs| Service[Business Services]
    Service -->|Entities| Repository[Spring Data JPA Repositories]
    Repository -->|SQL| Database[(PostgreSQL)]
    
    subgraph Spring Boot Application
        Controller
        Service
        Repository
    end
```
The services include `CandidateService` (orchestrates resume uploads and processing), `JobPostingService` (manages job details), `AuthService` (controls signup and JWT lifecycle), and `EmailService` (handles transactional emails).

### Asynchronous AI Resume Processing Pipeline
The resume processing workflow is fully asynchronous to prevent thread-blocking on the servlet container:

```mermaid
sequenceDiagram
    autonumber
    actor Recruiter
    participant Frontend as Next.js Frontend
    participant Backend as Spring Boot Backend
    participant Storage as Cloudinary CDN
    participant AI as FastAPI AI Service
    participant LLM as OpenRouter / Gemini

    Recruiter->>Frontend: Upload candidate resume file (PDF/DOCX)
    Frontend->>Backend: Request signed upload payload
    Backend-->>Frontend: Return secure upload signature
    Frontend->>Storage: POST file directly to Cloudinary
    Storage-->>Frontend: Return public secure file URL
    Frontend->>Backend: POST /api/candidates (Candidate info + file URL)
    Note over Backend: Save Candidate (status: NEW)
    Backend-->>Frontend: Return HTTP 201 Created (Candidate saved)
    Note over Backend: Trigger processCandidateResumeAsync()
    Backend->>AI: POST /internal/process-resume (Payload + internal auth header)
    AI-->>Backend: Return HTTP 202 Accepted
    Note over AI: Run uvicorn background task
    AI->>Storage: Download file bytes
    AI->>AI: Extract raw text from file bytes
    AI->>LLM: Invoke structured ChatPromptChain
    LLM-->>AI: Return JSON output (scores, matched/missing skills, summary)
    AI->>Backend: POST /api/internal/ai-webhook (Webhook callback payload)
    Note over Backend: Parse callback and save CandidateScore
    Note over Backend: Update Candidate status to SCORED
```

### Recruiter Authentication Flow
```mermaid
graph TD
    A[Signup] -->|Send verification email| B[Email Verification Token]
    B -->|Click activation link| C[Verify Endpoint]
    C -->|Activate Recruiter| D[Login]
    D -->|Post credentials| E{Credentials Valid?}
    E -->|No| F[401 Unauthorized]
    E -->|Yes| G[Generate JWT Access & Refresh Tokens]
    G -->|Set HTTP-Only Cookie| H[Refresh Token]
    G -->|Return JSON| I[Access Token]
```

### Production Deployment Architecture
```mermaid
graph TB
    subgraph Vercel
        Frontend[Next.js App]
    end
    subgraph Render Private Network
        Backend[Spring Boot Backend App<br>Dockerized Container]
        AIService[FastAPI AI App<br>Python Web Service]
        Database[(Managed PostgreSQL)]
    end
    
    Frontend -->|REST Calls| Backend
    Backend -->|Local Connection| Database
    Backend -->|Internal REST| AIService
    AIService -->|Internal Webhook| Backend
```

---

## 📂 Folder Structure

```
ResumeRank_AI/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml           # Backend CI (Lint, Test, Coverage, Docker-check)
│       └── quality-gate.yml         # Aggregated branch protection check
├── aiservice/
│   ├── .dockerignore
│   ├── .env.example
│   ├── .gitignore
│   ├── main.py                      # FastAPI microservice entry point & LLM prompt logic
│   ├── requirements.txt             # Python packages (langchain, fastapi, pypdf)
│   └── test_main.py                 # FastAPI routing and extraction unit tests
├── backend/
│   ├── .dockerignore
│   ├── .env.example
│   ├── .gitignore
│   ├── Dockerfile                   # Multi-stage Java 21 production Dockerfile
│   ├── pom.xml                      # Maven configuration (Spring Boot, Testcontainers, JaCoCo)
│   └── src/
│       ├── main/
│       │   ├── java/com/resumerank/backend/
│       │   │   ├── config/              # Security, CORS, Rate Limiting, RestTemplate
│       │   │   ├── controller/          # REST Endpoint Controllers (Job, Candidate, Webhook)
│       │   │   ├── dto/                 # Request & Response Data Transfer Objects
│       │   │   ├── entity/              # JPA Database Models (JSR-380 validation, Postgres Arrays)
│       │   │   ├── exception/           # Exception definitions & Global Handler
│       │   │   ├── repository/          # Spring Data JPA Repository Interfaces
│       │   │   └── service/             # Core Business Logic (Candidate, Job, JWT, Email)
│       │   └── resources/
│       │       ├── db/migration/        # Flyway DB schema migration scripts
│       │       ├── application.yml      # Base Spring Boot Configuration (secured env vars)
│       │       └── templates/           # Thymeleaf verification & reset email templates
│       └── test/
│           ├── java/com/resumerank/backend/
│           │   ├── controller/          # MockMvc Endpoint Integration Tests
│           │   ├── service/             # Unit and mock service tests
│           │   └── support/             # Testcontainers Postgres bootstrap helper base
│           └── resources/
│               └── application-test.yml # Spring active test profile configuration
├── frontend/
│   ├── src/
│   │   ├── app/                     # Next.js App Router Pages and API Route Handlers
│   │   ├── components/              # Shared UI components (tables, inputs, buttons)
│   │   ├── context/                 # AuthContext (recruiter state & JWT refresh timer)
│   │   └── lib/                     # Axios API clients, Cloudinary BFF upload helpers
│   ├── package.json                 # Next.js, tailwindcss dependencies
│   ├── tsconfig.json
│   └── vitest.config.ts             # Vitest frontend suite configurations
└── README.md                        # Project documentation (this file)
```





