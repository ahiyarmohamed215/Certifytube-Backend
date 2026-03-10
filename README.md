<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/MySQL-8.x-4479A1?style=for-the-badge&logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/Python-ML%20Service-3776AB?style=for-the-badge&logo=python&logoColor=white" />
  <img src="https://img.shields.io/badge/License-Academic-blue?style=for-the-badge" />
</p>

# 🎓 CertifyTube — Backend

> **AI-Powered YouTube Learning Verification & Certification Platform**

CertifyTube is a full-stack learning platform that uses **machine learning** to verify genuine learner engagement on YouTube educational videos and issues tamper-proof, employer-verifiable digital certificates. This repository contains the **Spring Boot backend** (REST API, business logic, PDF generation, and data persistence).

---

## 📋 Table of Contents

- [Problem Statement](#-problem-statement)
- [Solution Overview](#-solution-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Features](#-features)
- [Database Schema](#-database-schema)
- [API Endpoints](#-api-endpoints)
- [Prerequisites](#-prerequisites)
- [Installation & Setup](#-installation--setup)
- [Configuration](#%EF%B8%8F-configuration)
- [Running](#-running)
- [Project Structure](#-project-structure)
- [ML Pipeline Integration](#-ml-pipeline-integration)
- [Certificate System](#-certificate-system)
- [Security](#-security)
- [Testing](#-testing)
- [Future Enhancements](#-future-enhancements)
- [Contributing](#-contributing)
- [Authors](#-authors)
- [License](#-license)

---

## ❓ Problem Statement

Online learning platforms lack **verifiable proof of genuine engagement**. Learners can claim to have "watched" a video without truly understanding the content. Employers have no way to validate whether a candidate actually engaged with the learning material.

## 💡 Solution Overview

CertifyTube addresses this by implementing a **dual-layer verification system**:

1. **Layer 1 — Engagement Verification (ML):** Tracks fine-grained video playback events (play, pause, seek, buffering, speed changes) and feeds them through trained **XGBoost** and **EBM (Explainable Boosting Machine)** models to predict genuine engagement vs. passive watching.

2. **Layer 2 — Knowledge Verification (Quiz):** AI-generated quizzes based on video transcripts test comprehension. Only learners who pass both layers receive a certificate.

3. **Tamper-Proof Certification:** Certificates are generated server-side with unique IDs, QR codes, and public verification URLs that employers can independently verify.

---

## 🏗 Architecture

```
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────┐
│   React      │    │   Spring Boot        │    │   Python ML      │
│   Frontend   │◄──►│   Backend (this)     │◄──►│   Service        │
│   (SPA)      │    │   :8080              │    │   :8000           │
└──────────────┘    └──────────┬───────────┘    └──────────────────┘
                               │                         │
                    ┌──────────▼───────────┐    ┌────────▼─────────┐
                    │   MySQL 8.x          │    │  XGBoost / EBM   │
                    │   (certifytube)       │    │  Models          │
                    └──────────────────────┘    └──────────────────┘
                               │
                    ┌──────────▼───────────┐
                    │   YouTube Data API   │
                    │   v3                 │
                    └──────────────────────┘
```

**Request Flow:**
```
User watches video → Frontend tracks events → Backend stores events
→ User clicks "Analyze" → Backend extracts 40+ features → ML service predicts engagement
→ Engagement ≥ 85% → Quiz generated (ML + transcript) → User answers
→ Quiz ≥ 80% → Certificate issued (PDF + QR code) → Stored in DB
→ Employer scans QR / opens link → Public verification page confirms validity
```

---

## 🛠 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Runtime** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 4.0.2 |
| **ORM** | Spring Data JPA / Hibernate | — |
| **Database** | MySQL | 8.x |
| **Auth** | JWT (jjwt 0.12.6) | — |
| **PDF Generation** | Apache PDFBox | 3.0.4 |
| **QR Codes** | Google ZXing | 3.5.3 |
| **Object Mapping** | MapStruct | 1.6.3 |
| **HTTP Client** | Spring WebFlux (WebClient) | — |
| **ML Service** | Python (FastAPI) | External |
| **Build Tool** | Maven (mvnw wrapper included) | — |

---

## ✨ Features

### Core Learning Flow
- 🔍 **YouTube Video Search** — Search and discover STEM educational videos via YouTube Data API v3
- 📺 **Session Management** — Start, resume, end, and delete learning sessions with position tracking
- 📊 **Event Tracking** — Batch ingestion of granular playback events (play, pause, seek, buffering, rate changes)
- 🧮 **Feature Engineering** — Extracts 40+ behavioral features from raw events (watch ratio, pause frequency, seek patterns, speed consistency, etc.)

### AI/ML Verification
- 🤖 **Engagement Prediction** — XGBoost and EBM models predict genuine engagement (0.0–1.0 score)
- 📖 **Explainability** — Top positive/negative feature contributions returned with every prediction
- 📝 **Quiz Generation** — AI-generated MCQ questions from video transcripts via ML service
- ✅ **Automated Grading** — Flexible answer matching (letter, number, text, boolean normalization)

### Certification
- 🏆 **Certificate Issuance** — Auto-generated upon passing both verification layers
- 📄 **PDF Generation** — Professional landscape A4 certificates with branding, scores, thresholds, and official seal
- 📱 **QR Code** — Each certificate embeds a QR code linking to its public verification URL
- 🔗 **Public Verification** — Employer-facing endpoint to independently verify certificate authenticity
- 🚫 **Admin Revocation** — Administrators can revoke certificates, with status reflected in verification

### Platform
- 🔐 **JWT Authentication** — Stateless auth with token revocation and automatic cleanup
- 👤 **User Management** — Signup, login, role-based access (LEARNER / ADMIN)
- 📋 **Dashboard** — Filterable session history by status (ACTIVE, COMPLETED, QUIZ_PENDING, CERTIFIED)
- ⚙️ **Admin Panel** — Full CRUD for users, sessions, certificates, and quizzes
- 🌿 **STEM Gating** — Only STEM-eligible videos qualify for certification (category + keyword fallback)
- ♻️ **Idempotency** — Analysis and quiz generation are idempotent to prevent duplicate operations

---

## 🗄 Database Schema

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   user_accounts  │     │     sessions     │     │  session_events  │
├──────────────────┤     ├──────────────────┤     ├──────────────────┤
│ id (PK)          │◄────│ user_id (FK)     │◄────│ session_id (FK)  │
│ email            │     │ session_id (PK)  │     │ id (PK)          │
│ password_hash    │     │ video_id         │     │ event_type       │
│ role             │     │ video_title      │     │ player_state     │
└──────────────────┘     │ status           │     │ current_time_sec │
                         │ video_duration   │     │ video_duration   │
                         │ last_position    │     │ playback_rate    │
                         └────────┬─────────┘     │ client_event_ms  │
                                  │               └──────────────────┘
                    ┌─────────────┼─────────────┐
                    ▼             ▼              ▼
          ┌─────────────┐ ┌────────────┐ ┌──────────────┐
          │ engagement  │ │   quizzes  │ │ certificates │
          │ _results    │ ├────────────┤ ├──────────────┤
          ├─────────────┤ │ quiz_id    │ │ cert_id (PK) │
          │ id (PK)     │ │ session_id │ │ user_id      │
          │ session_id  │ │ user_id    │ │ session_id   │
          │ model_used  │ │ video_id   │ │ status       │
          │ eng_score   │ │ difficulty │ │ eng_score    │
          │ threshold   │ └─────┬──────┘ │ quiz_score   │
          │ status      │       │        │ thresholds   │
          │ explanation │       ▼        │ video_title  │
          └─────────────┘ ┌────────────┐ │ video_dur    │
                          │ quiz_      │ │ pdf_bytes    │
          ┌─────────────┐ │ questions  │ │ verify_token │
          │ session_    │ ├────────────┤ │ learner_name │
          │ features    │ │ quiz (FK)  │ │ created_at   │
          ├─────────────┤ │ q_text     │ └──────────────┘
          │ session_id  │ │ options    │
          │ 40+ feature │ │ correct    │
          │ columns     │ └────────────┘
          └─────────────┘
                          ┌────────────┐
                          │ quiz_      │
                          │ attempts   │
                          ├────────────┤
                          │ quiz (FK)  │
                          │ user_id    │
                          │ answers    │
                          │ score_%    │
                          │ passed     │
                          └────────────┘
```

Additional tables: `youtube_search_cache`, `youtube_search_cache_items`, `youtube_video_cache`, `revoked_tokens`, `session_features`

---

## 🌐 API Endpoints

### Authentication (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | Register new user |
| POST | `/api/auth/login` | Login and receive JWT |
| GET | `/api/auth/me` | Get current user info |
| POST | `/api/auth/logout` | Revoke JWT token |

### YouTube Search (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/youtube/search?q=...&limit=20` | Search YouTube videos |

### Sessions (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions/start` | Start or resume a session |
| POST | `/api/sessions/end?sessionId=...` | End a session |
| DELETE | `/api/sessions/{id}` | Delete a session |
| POST | `/api/sessions/{id}/analyze` | Analyze engagement (ML) |

### Events (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/events/batch` | Batch ingest playback events |

### Dashboard (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard` | Get all sessions grouped by status |
| GET | `/api/dashboard?status=ACTIVE` | Filter by status |

### Quiz (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/quiz/eligibility?sessionId=...` | Check quiz eligibility |
| POST | `/api/quiz/generate` | Generate AI quiz |
| GET | `/api/quiz/{quizId}` | Get quiz questions |
| POST | `/api/quiz/{quizId}/submit` | Submit quiz answers |
| GET | `/api/quiz/{quizId}/result` | Get attempt result |

### Certificates (Mixed Auth)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/certificates/{id}` | JWT | Get owned certificate |
| GET | `/api/certificates/{id}/pdf` | JWT | Download certificate PDF |
| GET | `/api/certificates/verify/{token}` | **None** | Public verification |

### Admin (ADMIN Role)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/stats` | System statistics |
| GET | `/api/admin/users` | List all users |
| PUT | `/api/admin/users/{id}/role` | Change user role |
| DELETE | `/api/admin/users/{id}` | Delete user |
| GET | `/api/admin/sessions` | List all sessions |
| DELETE | `/api/admin/sessions/{id}` | Delete session |
| GET | `/api/admin/certificates` | List all certificates |
| DELETE | `/api/admin/certificates/{id}` | Delete certificate |
| POST | `/api/admin/certificates/{id}/revoke` | Revoke certificate |
| GET | `/api/admin/quizzes` | List all quizzes |
| DELETE | `/api/admin/quizzes/{id}` | Delete quiz |

> 📖 For complete request/response schemas, see [FRONTEND_GUIDE.md](./FRONTEND_GUIDE.md)

---

## 📦 Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java JDK** | 21+ | [Download](https://adoptium.net/) |
| **MySQL** | 8.0+ | Create database `certifytube` |
| **Python ML Service** | 3.10+ | Runs on port `8000` — see ML repo |
| **YouTube Data API Key** | v3 | [Google Cloud Console](https://console.cloud.google.com/) |
| **Maven** | — | Included via `mvnw` wrapper |

---

## 🚀 Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/certifytube-backend.git
cd certifytube-backend
```

### 2. Create the MySQL Database

```sql
CREATE DATABASE certifytube CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> Hibernate auto-creates all tables on first run (`ddl-auto=update`).

### 3. Set Environment Variables

```bash
# Required
export AUTH_JWT_SECRET="your-256-bit-secret-key-here-min-32-chars"
export YOUTUBE_API_KEY="AIzaSy..."

# Optional (defaults shown)
export APP_PUBLIC_BASE_URL="http://localhost:8080"
```

**On Windows (PowerShell):**
```powershell
$env:AUTH_JWT_SECRET = "your-256-bit-secret-key-here-min-32-chars"
$env:YOUTUBE_API_KEY = "AIzaSy..."
```

### 4. Start the ML Service

```bash
# In the ML service directory
cd ../ml-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 5. Build & Run

```bash
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

The server starts at **http://localhost:8080**.

---

## ⚙️ Configuration

All configuration is in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/certifytube` | Database URL |
| `spring.datasource.username` | `root` | DB username |
| `spring.datasource.password` | `root` | DB password |
| `ml.base-url` | `http://localhost:8000` | ML service URL |
| `ml.default-model` | `xgboost` | Default ML model (`xgboost` or `ebm`) |
| `ml.engagement-threshold` | `0.85` | Engagement pass threshold |
| `quiz.min-engagement-score` | `0.85` | Min engagement to unlock quiz |
| `quiz.pass-score` | `80` | Quiz pass mark (%) |
| `quiz.max-failed-attempts` | `2` | Max quiz retries per engagement window |
| `auth.jwt.expiration-minutes` | `120` | JWT token TTL |
| `youtube.cache-ttl-minutes` | `1440` | YouTube search cache (24h) |
| `app.public-base-url` | `http://localhost:8080` | Public URL for certificate links |

---

## ▶️ Running

```bash
# Development
./mvnw spring-boot:run

# Production build
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar

# Windows
.\mvnw.cmd spring-boot:run
```

---

## 📂 Project Structure

```
src/main/java/com/certifytube/backend/
├── BackendApplication.java              # Entry point
├── client/                              # External service clients
│   ├── MlServiceClient.java            #   → Python ML service (WebClient)
│   └── YouTubeClient.java             #   → YouTube Data API v3
├── config/                              # Spring configuration
│   └── SecurityConfig.java             #   → JWT filter chain, CORS, roles
├── controller/                          # REST controllers
│   ├── AdminCertificateController.java #   → Certificate revocation
│   ├── AdminController.java            #   → Admin CRUD panel
│   ├── AuthController.java             #   → Signup, login, logout
│   ├── CertificateController.java      #   → Get cert, PDF, verify
│   ├── DashboardController.java        #   → Session dashboard
│   ├── EventBatchController.java       #   → Playback event ingestion
│   ├── QuizController.java             #   → Quiz lifecycle
│   ├── SessionAnalyzeController.java   #   → Engagement analysis trigger
│   ├── SessionController.java          #   → Session CRUD
│   └── YouTubeController.java          #   → Video search
├── dto/                                 # Data Transfer Objects (24 files)
├── exception/                           # Custom exceptions (3 files)
├── mapper/                              # MapStruct mappers (5 files)
├── model/                               # JPA entities (14 files)
│   ├── Certificate.java                #   → Immutable cert record
│   ├── EngagementResult.java           #   → ML prediction result
│   ├── Session.java                    #   → Learning session
│   ├── SessionEvent.java              #   → Raw playback event
│   ├── SessionFeatures.java           #   → 40+ engineered features
│   ├── Quiz.java / QuizQuestion.java  #   → AI-generated quiz
│   ├── QuizAttempt.java               #   → Learner's quiz submission
│   └── UserAccount.java               #   → User with role
├── repository/                          # Spring Data JPA repos (13 files)
├── security/                            # JWT filter & user details (3 files)
├── service/                             # Business logic (17 files)
│   ├── CertificateService.java        #   → Issue, verify, revoke, PDF gen
│   ├── FeatureEngineeringServiceImpl.java #   → 40+ feature extraction
│   ├── QuizService.java               #   → Generate, grade, attempt mgmt
│   ├── SessionAnalyzeServiceImpl.java #   → ML prediction orchestration
│   └── SessionEventServiceImpl.java   #   → Event batch processing
└── util/                                # Utilities
    └── StemCategoryUtil.java           #   → STEM eligibility checker

src/main/resources/
├── application.properties               # Configuration
├── feature_contract_v1.json            # ML feature schema contract
└── seal/
    └── certifytube_seal.png            # Official certification seal
```

---

## 🤖 ML Pipeline Integration

The backend communicates with an external **Python ML service** via REST (WebClient):

### Engagement Prediction Flow
```
1. SessionEventService collects raw playback events
2. FeatureEngineeringService extracts 40+ features:
   - watch_ratio, pause_frequency, avg_pause_duration
   - seek_count, seek_back_ratio, speed_changes
   - active_watch_sec, idle_gaps, buffer_ratio
   - completion_flag, session_duration_ratio, etc.
3. Features are validated against feature_contract_v1.json
4. MlServiceClient sends features to ML service
5. ML service returns: engagement_score (0-1), prediction, explanation
6. Result stored in engagement_results table
```

### Quiz Generation Flow
```
1. MlServiceClient sends video_id + transcript to ML service
2. ML service generates MCQ questions from transcript
3. Questions stored in quiz_questions table
4. Flexible grading: letter (a/b/c), number (1/2/3), text, boolean matching
```

### Supported Models
| Model | Type | Explainability |
|-------|------|----------------|
| **XGBoost** | Gradient Boosted Trees | SHAP-based feature importance |
| **EBM** | Explainable Boosting Machine | Interpretable Glass-Box model |

---

## 🏆 Certificate System

### Issuance Conditions (Both Required)
1. ✅ Engagement Score ≥ **85%** (ML-verified)
2. ✅ Quiz Score ≥ **80%** (knowledge-verified)

### Certificate Contents
| Element | Source |
|---------|--------|
| Learner Name | Derived from email |
| Platform Name | "CertifyTube" |
| Course / Video Title | YouTube video title |
| Engagement Score | ML prediction (locked at issuance) |
| Quiz Score | Quiz attempt score (locked at issuance) |
| Pass Thresholds | Engagement & quiz thresholds (locked at issuance) |
| Video Duration | From session data |
| YouTube Link | Derived from video ID |
| Issue Date | UTC timestamp |
| Unique Certificate ID | UUID |
| QR Code | Links to public verification URL |
| Official Seal | CertifyTube certified seal image |
| Signature Lines | AI Assessment Engine + Dual-Verification ML |

### Verification
- **Public URL:** `GET /api/certificates/verify/{token}` — no auth required
- **QR Code:** Scannable, links to verification URL
- **Status:** Certificates can be `ACTIVE` or `REVOKED` (admin-only)

### PDF Generation
- **Format:** Landscape A4, generated server-side with Apache PDFBox
- **Storage:** Binary PDF stored in database (`LONGBLOB`)
- **Export:** Download via `GET /api/certificates/{id}/pdf`

---

## 🔐 Security

| Feature | Implementation |
|---------|----------------|
| **Authentication** | JWT (HS256) via `jjwt 0.12.6` |
| **Token Expiry** | 120 minutes (configurable) |
| **Token Revocation** | Logout revokes token, periodic cleanup job |
| **Password Hashing** | BCrypt |
| **Role-Based Access** | `LEARNER` and `ADMIN` roles |
| **Stateless Sessions** | No server-side session state |
| **Resource Ownership** | All endpoints validate resource ownership |
| **Public Endpoints** | Only auth, search, and certificate verification |
| **Admin Endpoints** | `/api/admin/**` → requires `ROLE_ADMIN` |

---

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with verbose output
./mvnw test -Dspring-boot.test.randomPort=true
```

---

## 🔮 Future Enhancements

- [ ] Email-based certificate delivery
- [ ] Batch certificate export (ZIP)
- [ ] Social sharing (LinkedIn, Twitter)
- [ ] Multi-language quiz support
- [ ] Real-time engagement score live preview
- [ ] WebSocket event streaming (replace polling)
- [ ] Rate limiting and API throttling
- [ ] Swagger / OpenAPI documentation
- [ ] Docker Compose for full-stack deployment
- [ ] CI/CD pipeline (GitHub Actions)

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## 👥 Authors

- **Your Name** — *Full Stack Developer* — [GitHub](https://github.com/your-username)

---

## 📄 License

This project is developed as a **Final Year Project** for academic purposes.

---

<p align="center">
  <b>Built with ❤️ using Spring Boot, Machine Learning, and a passion for verifiable learning</b>
</p>
