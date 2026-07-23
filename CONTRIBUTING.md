# Contributing to ResumeRank AI

First off, thank you for taking the time to contribute! Contributions from the community make projects like ResumeRank AI great.

This document guides you through the guidelines and process for contributing to our repository.

---

## 📖 Table of Contents
1. [Code of Conduct](#-code-of-conduct)
2. [Project Architecture](#-project-architecture)
3. [Development Setup](#-development-setup)
4. [Database Migrations](#-database-migrations)
5. [Testing Strategy](#-testing-strategy)
6. [Coding Style & Standards](#-coding-style--standards)
7. [Git Guidelines](#-git-guidelines)
8. [Pull Request Process](#-pull-request-process)
9. [Reporting Issues](#-reporting-issues)
10. [Security Policy](#-security-policy)

---

## 🤝 Code of Conduct

We are committed to fostering a welcoming, respectful, and harassment-free environment for everyone. By participating in this project, you agree to abide by standard professional code of conduct rules:
- Be respectful and constructive in your feedback and discussions.
- Focus on what is best for the community and project.
- Gracefully accept constructive criticism.

---

## 🏗️ Project Architecture

ResumeRank AI is built as a multi-service monorepo:
1. **`frontend/`**: A React/Next.js client compiled using Vite.
2. **`backend/`**: A Java 21/Spring Boot REST API handling security, transactional operations, and callback orchestrations.
3. **`aiservice/`**: A Python 3.10+/FastAPI microservice executing text extraction and LLM scoring workflows.

---

## 💻 Development Setup

### Prerequisites
Before starting, ensure you have the following installed:
- **Java JDK 21** (e.g., Eclipse Temurin)
- **Maven 3.9+**
- **Python 3.10+ & pip**
- **Node.js 18+ & npm**
- **Docker Engine** (to host database containers during testing)

### Step 1: Clone the Repository
```bash
git clone https://github.com/Suraj-codes1410/ResumeRank_AI.git
cd ResumeRank_AI
```

### Step 2: Spin Up the Database
A PostgreSQL instance is defined in the root `docker-compose.yml`. Start it locally:
```bash
docker compose up -d db
```

### Step 3: Configure Environment Files
1. **Backend**: Copy `backend/.env.example` to `backend/.env` and supply your database credentials, JWT secret, Cloudinary properties, and Resend details.
2. **AI Service**: Copy `aiservice/.env.example` to `aiservice/.env` and supply your `OPENROUTER_API_KEY` and shared `INTERNAL_SERVICE_TOKEN`.

### Step 4: Run Services

#### Backend Service
```bash
cd backend
mvn spring-boot:run
```
The server will boot on `http://localhost:8081`.

#### AI Service
```bash
cd aiservice
python -m venv venv
# Windows:
.\venv\Scripts\activate
# Linux/macOS:
source venv/bin/activate

pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```
Swagger UI will be accessible at `http://localhost:8000/docs`.

#### Frontend Client
```bash
cd frontend
npm install
npm run dev
```
The client app will open on `http://localhost:3000`.

---

## 🗄️ Database Migrations

We use **Flyway** for database schema versioning.
- SQL migration scripts are located in [`backend/src/main/resources/db/migration/`](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/backend/src/main/resources/db/migration/).
- Never modify an existing migration script once it has been committed/merged.
- To make schema changes, create a new SQL file following the naming convention: `V{VERSION}__your_change_description.sql` (e.g., `V8__add_job_department.sql`).

---

## 🧪 Testing Strategy

Tests are run automatically during CI checks. Ensure they pass locally before pushing.

### Backend Testing
- **Unit Tests**: Focus on business logic isolated via mocks:
  ```bash
  mvn test
  ```
- **Integration Tests**: Bootstraps the full Spring context and runs against a real PostgreSQL container managed by **Testcontainers**:
  ```bash
  mvn verify
  ```

### AI Service Testing
- Runs unit tests for extraction parsers and API routers:
  ```bash
  pytest
  ```

### Frontend Testing
- Runs unit and rendering test suites using Vitest:
  ```bash
  npm run test
  ```

---

## 🎨 Coding Style & Standards

- **Java**: Follow standard Spring framework naming and checkstyle conventions.
- **Python**: Adhere to PEP 8 standards. Use `black` or `autopep8` for code formatting.
- **TypeScript/React**: Follow standard ESLint configs defined in the project. Use camelCase for variables/functions and PascalCase for components.

---

## 🐙 Git Guidelines

### Branch Naming Conventions
Create descriptive branch names prefixed by their purpose:
- `feature/` for new capabilities (e.g., `feature/azure-ad-sso`)
- `bugfix/` for resolving issues (e.g., `bugfix/token-refresh-leak`)
- `docs/` for documentation updates (e.g., `docs/contributing-guide`)
- `refactor/` for code restructuring (e.g., `refactor/api-response-types`)

### Conventional Commit Messages
We follow the [Conventional Commits](https://www.conventionalcommits.org/) format. Make sure your commit messages start with:
- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation modifications
- `style:` for changes that do not affect code logic (formatting, spacing)
- `refactor:` for codebase reorganizations
- `test:` for adding or updating test coverages
- `chore:` for updating build configurations, dependencies, etc.

*Example*: `feat: add Google Cloud Storage backup support`

---

## 📥 Pull Request Process

1. **Create an Issue**: Before starting work on major features, open an issue to discuss design and intent.
2. **Branch**: Fork the repository or create a clean branch from `main`.
3. **Commit**: Keep commits small, logical, and well-described.
4. **Test**: Run full test suites locally.
5. **PR Description**: Include a clear description of the problem solved, changes introduced, and testing steps performed.
6. **Code Review**: At least one maintainer must review and approve your PR before it can be merged.

---

## 🐛 Reporting Issues

### Bug Reports
When submitting a bug report, please include:
- A clear, concise description of the bug.
- Steps to reproduce the behavior.
- Expected vs. actual behavior.
- System logs, stack traces, or console outputs.

### Feature Requests
When submitting a feature request, please describe:
- The problem you want solved.
- The proposed solution or behavior.
- Alternatives you have considered.

---

## 🔒 Security Policy

If you discover a security vulnerability in this project, **do not open a public GitHub issue**. Instead, please report it privately via email to: `security@resumerank.ai`.

---

## 📧 Contact
For general inquiries or maintainer coordination, reach out to **Suraj Samanta** at `maintainers@resumerank.ai`.
