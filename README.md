<p align="center">
  <strong>CertifyTube</strong><br>
  AI-powered engagement verification and certification for YouTube-based learning
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/ML-XGBoost%20%7C%20EBM-3776AB?logo=python&logoColor=white" />
</p>

---

CertifyTube verifies that learners genuinely engage with YouTube educational content using a dual-layer ML pipeline, then issues tamper-proof certificates that employers can independently verify via QR code or public URL.

**Backend service** â€” REST API, business logic, ML integration, PDF certificate generation, and data persistence.

## How It Works

```
Watch video â†’ Track events â†’ ML predicts engagement â†’ Pass? â†’ AI quiz â†’ Pass? â†’ Certificate
```

1. **Engagement layer** â€” Captures granular playback events (play, pause, seek, buffering, speed). Extracts 40+ behavioral features. XGBoost/EBM models score engagement (0â€“1).
2. **Knowledge layer** â€” AI-generated quiz from video transcript. Graded automatically.
3. **Certification** â€” Both layers pass â†’ server generates PDF with QR code â†’ stored immutably â†’ publicly verifiable.

## Quick Start

**Requirements:** Java 21+, MySQL 8+, Python ML service running on `:8000`

```bash
# 1. Database
mysql -u root -p -e "CREATE DATABASE certifytube CHARACTER SET utf8mb4;"

# 2. Environment
export AUTH_JWT_SECRET="your-256-bit-secret"
export YOUTUBE_API_KEY="AIzaSy..."

# 3. Run
./mvnw spring-boot:run
```

Server starts at `http://localhost:8080`. ML service expected at `http://localhost:8000`.

## Architecture

```
Frontend (React)  â”€â”€â”€â”€â”€â–º  Backend (Spring Boot :8080)  â”€â”€â”€â”€â”€â–º  ML Service (FastAPI :8000)
                                    â”‚                                    â”‚
                                    â–¼                                    â–¼
                              MySQL 8.x                          XGBoost / EBM
                                    â”‚
                                    â–¼
                          YouTube Data API v3
```

## API Reference

Full request/response schemas in [`FRONTEND_GUIDE.md`](./FRONTEND_GUIDE.md).

### Auth
| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/api/auth/signup` | â€” |
| POST | `/api/auth/login` | â€” |
| GET | `/api/auth/me` | JWT |
| POST | `/api/auth/logout` | JWT |

### Sessions & Events
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions/start` | Start or resume session |
| POST | `/api/events/batch` | Batch ingest playback events |
| POST | `/api/sessions/end` | End session |
| POST | `/api/sessions/{id}/analyze` | Trigger ML engagement analysis |
| DELETE | `/api/sessions/{id}` | Delete session |

### Quiz
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/quiz/eligibility` | Check eligibility |
| POST | `/api/quiz/generate` | Generate AI quiz |
| POST | `/api/quiz/{id}/submit` | Submit answers |
| GET | `/api/quiz/{id}/result` | Get result |

### Certificates
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/certificates/{id}` | JWT | Get certificate |
| GET | `/api/certificates/{id}/pdf` | JWT | Download PDF |
| GET | `/api/certificates/verify/{token}` | **Public** | Employer verification |

### Admin
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/learners` | List learners |
| GET | `/api/admin/learners/{learnerId}/profile` | Learner deep profile |
| DELETE | `/api/admin/certificates/{id}` | Delete certificate |
| POST | `/api/admin/certificates/{id}/revoke` | Revoke certificate |
| POST | `/api/admin/certificates/{id}/activate` | Activate certificate |

## Configuration

`src/main/resources/application.properties`

| Key | Default | Description |
|-----|---------|-------------|
| `ml.base-url` | `http://localhost:8000` | ML service endpoint |
| `ml.default-model` | `xgboost` | `xgboost` or `ebm` |
| `quiz.min-engagement-score` | `0.85` | Engagement pass threshold |
| `quiz.pass-score` | `80` | Quiz pass mark (%) |
| `quiz.max-failed-attempts` | `2` | Quiz retries per window |
| `auth.jwt.expiration-minutes` | `120` | Token TTL |
| `youtube.api-key` | â€” | YouTube Data API v3 key |
| `app.public-base-url` | `http://localhost:8080` | Base URL for cert links |

## Project Layout

```
src/main/java/com/certifytube/backend/
â”œâ”€â”€ client/          MlServiceClient, YouTubeClient
â”œâ”€â”€ config/          SecurityConfig (JWT filter, RBAC)
â”œâ”€â”€ controller/      10 REST controllers
â”œâ”€â”€ dto/             Request/response objects
â”œâ”€â”€ mapper/          MapStruct mappers
â”œâ”€â”€ model/           14 JPA entities
â”œâ”€â”€ repository/      13 Spring Data repos
â”œâ”€â”€ security/        JWT filter, UserDetailsService
â”œâ”€â”€ service/         Core business logic
â”‚   â”œâ”€â”€ CertificateService      Issue, verify, revoke, PDF generation
â”‚   â”œâ”€â”€ FeatureEngineering       40+ feature extraction from events
â”‚   â”œâ”€â”€ QuizService              Generate, grade, attempt management
â”‚   â”œâ”€â”€ SessionAnalyze           ML prediction orchestration
â”‚   â””â”€â”€ SessionEventService      Event batch processing
â””â”€â”€ util/            STEM eligibility checker
```

## Certificate System

**Issuance conditions** (both required):
- Engagement â‰¥ 85% (ML-verified)
- Quiz â‰¥ 80% (knowledge-verified)

**Certificate includes:** learner name, course title, scores, thresholds, video duration, YouTube link, issue date, unique ID, QR code, official seal, verification URL.

**Verification:** Public endpoint â€” no auth. Returns `status: "ACTIVE"` or `"REVOKED"` with `valid: true/false`. Employers scan QR or open link.

**Revocation:** Admin-only. `POST /api/admin/certificates/{id}/revoke`. Certificate persists but shows as revoked on verification.

**PDF:** Landscape A4, server-generated (PDFBox), stored as LONGBLOB. QR code generated via ZXing.

## ML Integration

```
Raw events â†’ FeatureEngineering (40+ features) â†’ ML Service â†’ Engagement score
                                                            â†’ Explainability (SHAP / EBM)
```

| Model | Type | Use Case |
|-------|------|----------|
| XGBoost | Gradient Boosted Trees | Primary model, SHAP explanations |
| EBM | Explainable Boosting Machine | Glass-box interpretable model |

Features include: `watch_ratio`, `pause_frequency`, `seek_count`, `speed_changes`, `active_watch_sec`, `buffer_ratio`, `completion_flag`, and 30+ more. Schema defined in `feature_contract_v1.json`.

## Security

- JWT (HS256) with configurable expiry and revocation
- BCrypt password hashing
- Role-based access: `LEARNER`, `ADMIN`
- Resource ownership validation on all endpoints
- Public endpoints: auth, YouTube search, certificate verification only

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 4.0.2 |
| Language | Java 21 |
| Database | MySQL 8.x + Hibernate |
| Auth | JWT (jjwt 0.12.6) |
| PDF | Apache PDFBox 3.0.4 |
| QR | Google ZXing 3.5.3 |
| Mapping | MapStruct 1.6.3 |
| HTTP | Spring WebFlux (WebClient) |
| ML | Python FastAPI (external) |
| Build | Maven (wrapper included) |

## Building

```bash
# Compile
./mvnw compile

# Package (skip tests)
./mvnw clean package -DskipTests

# Run JAR
java -jar target/backend-0.0.1-SNAPSHOT.jar

# Tests
./mvnw test
```

## License

Academic project. All rights reserved.

