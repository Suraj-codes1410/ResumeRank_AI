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

