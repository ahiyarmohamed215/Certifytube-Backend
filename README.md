# CertifyTube Backend

Backend service for CertifyTube, an AI-assisted learning verification platform that measures real learner engagement on YouTube-based educational content, generates adaptive quizzes, and issues verifiable certificates.

This repository is structured as a professional backend codebase for an academic capstone implementation. It provides REST APIs, security, persistence, ML integration, certificate generation, and audit-friendly request logging.

## Project Context

CertifyTube Backend is part of an IIT final-year project.

The objective of the system is to validate that a learner has:

- watched and interacted with video content in a meaningful way
- passed an AI-generated quiz based on the learning material
- earned a certificate that can be verified publicly

## Core Capabilities

- JWT-based authentication and role-based authorization
- Session lifecycle management for learning activity
- Batch ingestion of playback telemetry
- ML service integration for engagement scoring
- AI quiz generation and quiz attempt evaluation
- Certificate issuance, PDF generation, QR verification, activation, and revocation
- Admin reporting and learner profile APIs

## High-Level Flow

```text
User watches a video
-> frontend sends playback events
-> backend forwards one-session raw events to ML service
-> ML service scores learner engagement
-> backend generates quiz
-> learner submits quiz
-> backend issues certificate if all thresholds pass
-> employer or reviewer verifies certificate through public endpoint
```

## Architecture

```text
Frontend
  -> Spring Boot Backend
      -> MySQL
      -> External ML Service
      -> YouTube Data API
      -> Email Provider
```

## Technology Stack

| Area | Technology |
|------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Security | Spring Security + JWT |
| Database | MySQL 8 |
| ORM | Spring Data JPA / Hibernate |
| HTTP Clients | WebClient |
| Mapping | MapStruct |
| PDF | Apache PDFBox |
| QR Code | ZXing |
| Build | Maven Wrapper |

## Repository Layout

```text
src/main/java/com/certifytube/backend
|- client        External service clients
|- config        Security, async, web, logging
|- controller    REST endpoints
|- dto           Request and response models
|- exception     API error handling
|- mapper        DTO/entity mapping
|- model         JPA entities
|- repository    Persistence layer
|- security      JWT and user detail services
|- service       Business logic
`- util          Shared helper utilities
```

## Runtime Requirements

- Java 21
- Maven wrapper included in the repository
- MySQL 8 or compatible MySQL deployment
- External ML service reachable from the backend

## Required Configuration

The backend reads configuration from environment variables and `src/main/resources/application.properties`.

Minimum required variables for local startup:

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | MySQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `AUTH_JWT_SECRET` | JWT signing secret |
| `ML_BASE_URL` | Base URL of the ML service |
| `APP_PUBLIC_BASE_URL` | Public backend URL used in verification links |
| `APP_FRONTEND_BASE_URL` | Frontend URL used in email links |

Common optional variables:

| Variable | Purpose |
|----------|---------|
| `YOUTUBE_API_KEY` | YouTube Data API access |
| `APP_EMAIL_PROVIDER` | `brevo` or `smtp` |
| `BREVO_API_KEY` | Brevo transactional email integration |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP configuration |

## Local Development

Create the database first:

```bash
mysql -u root -p -e "CREATE DATABASE certifytube CHARACTER SET utf8mb4;"
```

Run the backend:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Default application port:

```text
http://localhost:8080
```

## Build and Test

Compile:

```bash
./mvnw compile
```

Windows:

```powershell
.\mvnw.cmd compile
```

Run tests:

```bash
./mvnw test
```

Package artifact:

```bash
./mvnw clean package -DskipTests
```

## Functional Areas

### Authentication

- signup, login, logout, email verification
- password reset and password change
- authenticated user profile lookup

### Sessions and Event Tracking

- start, resume, end, and delete learning sessions
- batch event ingestion for player telemetry
- ownership validation on all protected session resources

### ML and Quiz

- engagement analysis from one-session raw events
- ML prediction orchestration through the external ML service
- quiz generation, grading, retry limits, and result retrieval

### Certificates

- issue certificates after engagement and quiz thresholds pass
- download certificate PDF
- public verification by token
- admin revoke and reactivate workflows

## Security and Operational Notes

- JWT is used for stateless API authentication
- request logging includes request correlation id support
- resource ownership is enforced in services and controllers
- email delivery supports both SMTP and Brevo
- certificate verification is intentionally public

## Ownership and Usage Notice

This repository is an IIT final-year project and the original work of its author.

No permission is granted to copy, redistribute, reuse, publish, submit, or present this codebase or its contents as another person's work without the author's explicit written approval.

This repository is provided as proprietary academic project material. All rights are reserved by the author.

## License

See [LICENSE](./LICENSE) for the repository usage restrictions.
