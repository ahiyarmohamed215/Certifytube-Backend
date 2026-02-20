# CertifyTube — Frontend Integration Guide

> Base URL: `http://localhost:8080`  
> Auth: `Authorization: Bearer <JWT>`

---

## 1. Auth Flow

### Signup
```
POST /api/auth/signup
Body: { "email": "...", "password": "..." }
→ { "userId": 1, "email": "...", "role": "LEARNER", "token": "...", "tokenType": "Bearer" }
```

### Login
```
POST /api/auth/login
Body: { "email": "...", "password": "..." }
→ Same as signup
```

### Get Current User
```
GET /api/auth/me
→ { "userId": 1, "email": "...", "role": "LEARNER" }
```

### Logout
```
POST /api/auth/logout
→ { "message": "Logged out" }
```

**Frontend rules:**
- Store token in `localStorage` or equivalent
- Add `Authorization: Bearer <token>` to all protected requests
- On any `401`, clear token and redirect to login
- Call `/api/auth/me` on app boot to restore session

---

## 2. Video Search (Public)

```
GET /api/youtube/search?q=spring%20boot&limit=20
→ { "query": "...", "count": 20, "videos": [{ "videoId": "...", "title": "...", "iframeUrl": "..." }] }
```

---

## 3. Session Lifecycle

This is the core workflow. Order matters.

### Step 1: Start Session
```
POST /api/sessions/start
Body: { "videoId": "abc123", "videoTitle": "Video title" }
→ { "sessionId": "uuid" }
```
Save the `sessionId` — you'll need it for everything.

### Step 2: Send Events (during video playback)

Batch events periodically (every 5-10 seconds) while the video plays:

```
POST /api/events/batch
Body: [
  {
    "sessionId": "uuid",
    "eventType": "play",          // play | pause | seek | buffering | ratechange | ended
    "playerState": 1,             // 1=playing, 2=paused, 3=buffering, 0=ended
    "playbackRate": 1.0,
    "currentTimeSec": 10.2,
    "videoDurationSec": 600.0,
    "clientEventMs": 12345,       // use performance.now(), must be monotonic
    "seekFromSec": null,          // only for seek events
    "seekToSec": null             // only for seek events
  }
]
→ { "saved": 18, "rejected": 2, "errors": [...] }
```

**Important:**
- Do NOT send `userId` — derived from JWT
- Use `performance.now()` for `clientEventMs` (NOT `Date.now()`)
- Send `videoDurationSec` in at least the first and last checkpoint
- When video ends naturally, send an event with `eventType: "ended"`

### Step 3: End Session
```
POST /api/sessions/end?sessionId=<sessionId>
→ { "ended": true }
```
**Call this AFTER flushing the final event batch.** The session must be ended before analysis.

### Step 4: Analyze Engagement
```
POST /api/sessions/<sessionId>/analyze
POST /api/sessions/<sessionId>/analyze?model=ebm    ← optional, default: xgboost
```

**Response:**
```json
{
  "sessionId": "uuid",
  "model": "xgboost",
  "engagementScore": 0.92,
  "threshold": 0.85,
  "status": "ENGAGED",
  "explanation": "The primary factors influencing this score were...",
  "topPositive": [
    { "feature": "watch_time_ratio", "shap_value": 0.45, "feature_value": 0.837, "behavior_category": "coverage" }
  ],
  "topNegative": [
    { "feature": "num_buffering_events", "shap_value": -0.03, "feature_value": 3.0, "behavior_category": "playback_quality" }
  ]
}
```

**Notes:**
- `engagementScore` is 0.0–1.0 (probability)
- `status` is `"ENGAGED"` or `"NOT_ENGAGED"` (backend decides using threshold)
- `topPositive` / `topNegative` contain the top 3 features pushing toward/against engagement
- For XGBoost: each contributor has `shap_value`
- For EBM: each contributor has `contribution`
- The `model` query param is optional. Default: `xgboost`

---

## 4. Quiz Flow

### Check Eligibility
```
GET /api/quiz/eligibility?sessionId=<sessionId>
→ {
    "sessionId": "uuid",
    "eligible": true,
    "reason": "Eligible",
    "requiredEngagementScore": 0.85,
    "latestEngagementScore": 0.92,
    "engagementPassed": true,
    "maxFailedAttempts": 3,
    "failedAttemptsUsed": 0,
    "remainingAttempts": 3
  }
```

### Generate Quiz
```
POST /api/quiz/generate
Body: { "sessionId": "uuid", "difficulty": "medium" }
→ {
    "quizId": "uuid",
    "sessionId": "uuid",
    "videoId": "abc123",
    "videoTitle": "Video title",
    "difficulty": "medium",
    "totalQuestions": 10,
    "questions": [
      { "questionId": "q1", "questionType": "mcq", "questionText": "...", "options": ["A","B","C","D"] }
    ]
  }
```

### Submit Quiz
```
POST /api/quiz/<quizId>/submit
Body: { "answers": { "q1": "A", "q2": "true", "q3": "fill_value" } }
→ {
    "quizId": "uuid",
    "correctCount": 8,
    "totalCount": 10,
    "scorePercent": 80.0,
    "passed": true,
    "certificateId": "cert-uuid",
    "verificationLink": "http://localhost:8080/api/certificates/verify/<token>"
  }
```

### Get Quiz / Result
```
GET /api/quiz/<quizId>          → same shape as generate
GET /api/quiz/<quizId>/result   → same shape as submit
```

---

## 5. Certificates

### Get Certificate (owner only)
```
GET /api/certificates/<certificateId>
→ { "certificateId": "...", "certificateNumber": "...", ... }
```

### Download PDF
```
GET /api/certificates/<certificateId>/pdf
→ application/pdf binary
```

### Public Verify (anyone)
```
GET /api/certificates/verify/<verificationToken>
→ { "certificateId": "...", "certificateNumber": "...", ... }
```

---

## 6. Admin Panel (ADMIN role only)

All endpoints require `ROLE_ADMIN`. Any non-admin user gets `403`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/stats` | System counts |
| GET | `/api/admin/users` | List all users |
| GET | `/api/admin/users/{id}` | Get user |
| PUT | `/api/admin/users/{id}/role` | Change role: `{"role":"ADMIN"}` |
| DELETE | `/api/admin/users/{id}` | Delete user |
| GET | `/api/admin/sessions` | List sessions |
| DELETE | `/api/admin/sessions/{id}` | Delete session |
| GET | `/api/admin/certificates` | List certificates |
| DELETE | `/api/admin/certificates/{id}` | Delete certificate |
| GET | `/api/admin/quizzes` | List quizzes |
| DELETE | `/api/admin/quizzes/{id}` | Delete quiz |

---

## 7. Recommended Page Structure

```
/                    → Search page (public)
/login               → Login form
/signup              → Signup form
/watch/:videoId      → Video player + event tracking
/analyze/:sessionId  → Engagement results
/quiz/:quizId        → Quiz UI
/result/:quizId      → Quiz result + certificate download
/verify/:token       → Public certificate verification
/admin               → Admin dashboard (ADMIN only)
```

---

## 8. Complete User Flow (Sequence)

```
1. User signs up or logs in              → store JWT
2. User searches for a video             → GET /api/youtube/search
3. User clicks a video                   → POST /api/sessions/start
4. Video plays, events stream            → POST /api/events/batch (every 5-10s)
5. Video ends or user navigates away     → flush events, POST /api/sessions/end
6. User clicks "Analyze"                 → POST /api/sessions/{id}/analyze
7. Show engagement results               → display score, status, explanation, contributors
8. If ENGAGED → show "Take Quiz"         → GET /api/quiz/eligibility
9. User generates quiz                   → POST /api/quiz/generate
10. User answers questions               → POST /api/quiz/{id}/submit
11. If passed → show certificate          → GET /api/certificates/{id}/pdf
12. Share verification link               → GET /api/certificates/verify/{token}
```

---

## 9. Error Handling

| Status | Meaning | Frontend Action |
|--------|---------|-----------------|
| `400` | Validation error | Show error message |
| `401` | Not authenticated | Clear token → redirect to login |
| `403` | Forbidden (wrong role/ownership) | Show "Access denied" |
| `500` | Server error | Show generic error |

All error responses follow: `{ "status": 4xx, "error": "...", "message": "..." }`
