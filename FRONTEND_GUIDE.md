# CertifyTube Ã¢â‚¬â€ Frontend Integration Guide

> Base URL: `http://localhost:8080`  
> Auth: `Authorization: Bearer <JWT>`

---

## 1. Auth Flow

### Signup
```
POST /api/auth/signup
Body: { "email": "...", "password": "..." }
-> { "userId": 1, "email": "...", "role": "LEARNER", "emailVerified": false, "message": "Signup successful. Please verify your email to continue." }
```
Signup does not return JWT until email verification is completed.

### Login
```
POST /api/auth/login
Body: { "email": "...", "password": "..." }
-> { "userId": 1, "email": "...", "role": "LEARNER", "emailVerified": true, "token": "...", "tokenType": "Bearer" }
```
Login fails if email is not verified.

### Get Current User
```
GET /api/auth/me
-> { "userId": 1, "email": "...", "role": "LEARNER", "emailVerified": true }
```

### Logout
```
POST /api/auth/logout
-> { "message": "Logged out" }
```

### Forgot Password
```
POST /api/auth/forgot-password
Body: { "email": "user@example.com" }
-> { "message": "If the email exists, a recovery email has been sent." }
```
This endpoint sends a real email via SMTP when account exists.
Rate limited: handle `429` and show "Too many requests, try again later."

### Reset Password
```
POST /api/auth/reset-password
Body: { "token": "<reset-token>", "newPassword": "NewPassword@123" }
-> { "message": "Password reset successful" }
```

### Resend Verification Email
```
POST /api/auth/resend-verification
Body: { "email": "user@example.com" }
-> { "message": "If the email exists and is not verified, a verification email has been sent" }
```
Rate limited: handle `429` and show "Too many requests, try again later."

### Verify Email
```
GET /api/auth/verify-email?token=<verification-token>
-> { "message": "Email verified successfully" }
```

Production setup note:
- Backend must use public domains in `APP_FRONTEND_BASE_URL` and `APP_PUBLIC_BASE_URL`; otherwise emails will contain localhost links.

### Change Password (Logged In)
```
POST /api/auth/change-password
Body: { "currentPassword": "OldPassword@123", "newPassword": "NewPassword@123" }
-> { "message": "Password changed successfully" }
```

### Delete Account (Profile)
```
DELETE /api/auth/me
-> { "message": "Account deleted successfully" }
```
This removes the authenticated learner account and owned data (sessions, engagement, quizzes, certificates).

**Frontend rules:**
- Store token in `localStorage` or equivalent
- Add `Authorization: Bearer <token>` to all protected requests
- On any `401`, clear token and redirect to login
- Call `/api/auth/me` on app boot to restore session
- After successful account delete, clear token and redirect to login/landing page

---
## 2. Dashboard (Home Page Data)

After login, call the dashboard endpoint to populate the home page. Use the `status` query param to fetch only the statuses needed for each page.

### Get Dashboard (all statuses)
```
GET /api/dashboard
Ã¢â€ â€™ {
    "activeVideos": [...],
    "completedVideos": [...],
    "quizPendingVideos": [...],
    "certifiedVideos": [...]
  }
```

### Filtered by Status
```
GET /api/dashboard?status=ACTIVE
Ã¢â€ â€™ Only "Continue Watching" videos

GET /api/dashboard?status=COMPLETED,QUIZ_PENDING,CERTIFIED
Ã¢â€ â€™ My Learnings / History page
```

**Each video item:**
```json
{
  "sessionId": "uuid",
  "videoId": "abc123",
  "videoTitle": "Video title",
  "thumbnailUrl": "https://...",
  "lastPositionSec": 120.5,
  "videoDurationSec": 600.0,
  "progressPercent": 20.08,
  "status": "ACTIVE",
  "stemEligible": true,
  "engagementScore": null,
  "certificateId": null,
  "createdAt": "2026-03-04T15:30:00Z"
}
```

**Session statuses:**

| Status | Meaning | Frontend Action |
|--------|---------|-----------------|
| `ACTIVE` | Watching in progress | Show "Continue Watching" Ã¢â€ â€™ click resumes session |
| `COMPLETED` | Session ended, pending analysis | Show "Analyze Engagement" (STEM only) |
| `QUIZ_PENDING` | Engagement passed (Ã¢â€°Â¥ 0.85) | Show "Take Quiz" |
| `CERTIFIED` | Quiz passed, certificate issued | Show "View Certificate" |

**Frontend page mapping:**
- **Home page:** `GET /api/dashboard?status=ACTIVE` Ã¢â€ â€™ "Continue Watching" section
- **My Learnings:** `GET /api/dashboard?status=COMPLETED,QUIZ_PENDING,CERTIFIED` Ã¢â€ â€™ History / progress

---

## 3. Video Search (Public)

```
GET /api/youtube/search?q=spring%20boot&limit=20
Ã¢â€ â€™ { "query": "...", "count": 20, "videos": [{ "videoId": "...", "title": "...", "iframeUrl": "..." }] }
```

---

## 4. Session Lifecycle

### Step 1: Start Session
```
POST /api/sessions/start
Body: { "videoId": "abc123", "videoTitle": "Video title" }
Ã¢â€ â€™ {
    "sessionId": "uuid",
    "resumed": false,
    "lastPositionSec": null,
    "videoDurationSec": null,
    "stemEligible": true,
    "stemMessage": null
  }
```

**New fields explained:**
- `resumed` Ã¢â‚¬â€ `true` if an existing open session was found (user returning to same video)
- `lastPositionSec` Ã¢â‚¬â€ if resumed, seek the video player to this position
- `stemEligible` Ã¢â‚¬â€ `true` if video qualifies for certification (STEM content)
- `stemMessage` Ã¢â‚¬â€ if not STEM, show this message to the user:
  > *"Only STEM-based skill videos (How-to, Science, Technology, Education) are eligible for engagement analysis, quiz, and certification. You can still watch this video but no certificate will be issued."*

**Frontend behavior:**
- If `resumed == true`: seek video player to `lastPositionSec`
- If `stemEligible == false`: show `stemMessage` banner, hide Analyze/Quiz/Certificate buttons

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
Ã¢â€ â€™ { "saved": 18, "rejected": 2, "errors": [...] }
```

**Important:**
- Do NOT send `userId` Ã¢â‚¬â€ derived from JWT
- Use `performance.now()` for `clientEventMs` (NOT `Date.now()`)
- Send `videoDurationSec` in at least the first and last checkpoint
- When video ends naturally, send an event with `eventType: "ended"`
- Backend auto-tracks `lastPositionSec` from events (for session resume)

### Step 3: End Session
```
POST /api/sessions/end?sessionId=<sessionId>
Ã¢â€ â€™ { "ended": true }
```
**Call this AFTER flushing the final event batch.** The session must be ended before analysis.

### Delete Session 
```
DELETE /api/sessions/<sessionId>
Ã¢â€ â€™ { "message": "Session deleted successfully" }
```
Learners can delete their own sessions to remove them from history or "Continue Watching". This cascades to delete any related engagement results or quizzes.

### Step 4: Analyze Engagement (STEM only)

> **Ã¢Å¡Â Ã¯Â¸Â Non-STEM videos cannot be analyzed.** Backend returns an error if attempted.

```
POST /api/sessions/<sessionId>/analyze
POST /api/sessions/<sessionId>/analyze?model=ebm    Ã¢â€ Â optional, default: xgboost
```

**Response:**
```json
{
  "sessionId": "uuid",
  "model": "xgboost",
  "engagementScore": 0.92,
  "threshold": 0.85,
  "status": "ENGAGED",
  "explanation": "The primary factors influencing this score were..."
}
```

**Notes:**
- `engagementScore` is 0.0Ã¢â‚¬â€œ1.0 (probability)
- `status` is `"ENGAGED"` or `"NOT_ENGAGED"` (backend decides using threshold)
- The `model` query param is optional. Default: `xgboost`
- **Idempotency:** if called again within 60 seconds, returns the cached result (prevents double-clicks)
- After analysis: if engaged Ã¢â€ â€™ session moves to `QUIZ_PENDING` status

**Frontend tip Ã¢â‚¬â€ disable the Analyze button** after the first click until the response comes back.

---

## 5. Quiz Flow

> **Ã¢Å¡Â Ã¯Â¸Â Non-STEM videos are not eligible for quiz.** `eligibility` will return `eligible: false`.

### Check Eligibility
```
GET /api/quiz/eligibility?sessionId=<sessionId>
Ã¢â€ â€™ {
    "sessionId": "uuid",
    "eligible": true,
    "reason": "Eligible",
    "requiredEngagementScore": 0.85,
    "latestEngagementScore": 0.92,
    "engagementPassed": true,
    "maxFailedAttempts": 2,
    "failedAttemptsUsed": 0,
    "remainingAttempts": 2,
    "stemEligible": true
  }
```

### Generate Quiz
```
POST /api/quiz/generate
Body: { "sessionId": "uuid", "difficulty": "medium" }
Ã¢â€ â€™ {
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

**Optional fields:**
- `numQuestions` (Integer) Ã¢â‚¬â€ override number of questions (1Ã¢â‚¬â€œ20)
- `includeCoding` (Boolean) Ã¢â‚¬â€ include coding questions

**Notes:**
- Backend auto-fetches transcript from YouTube (via ML server) Ã¢â‚¬â€ no need to send transcript
- **Idempotency:** if called again within 60 seconds, returns the existing quiz
- **Disable the Generate button** after click to prevent duplicate calls
- `generate/get` does not include `correctAnswer` or `explanation`.

### Submit Quiz
```
POST /api/quiz/<quizId>/submit
Body: { "answers": { "q1": "A", "q2": "true", "q3": "fill_value" } }
Ã¢â€ â€™ {
    "quizId": "uuid",
    "correctCount": 8,
    "totalCount": 10,
    "scorePercent": 80.0,
    "passed": true,
    "certificateId": "cert-uuid",
    "verificationLink": "http://localhost:8080/api/certificates/verify/<token>",
    "review": [
      {
        "questionId": "q1",
        "questionType": "mcq",
        "questionText": "...",
        "options": ["A","B","C","D"],
        "selectedAnswer": "A",
        "correctAnswer": "B",
        "correct": false,
        "explanation": "..."
      }
    ]
  }
```
`answers` keys should use `questionId` from quiz response; fallback `q1`, `q2`, ... is accepted.
Use `review[]` to show each question's correct answer + explanation after submit (pass or fail).
After 2 failed attempts in the current engagement window, learner must rewatch + analyze again.

### Get Quiz / Result
```
GET /api/quiz/<quizId>          Ã¢â€ â€™ same shape as generate
GET /api/quiz/<quizId>/result   Ã¢â€ â€™ same shape as submit
```

---

## 6. Certificates

### Get Certificate (owner only)
```
GET /api/certificates/<certificateId>
Ã¢â€ â€™ {
    "certificateId": "uuid",
    "certificateNumber": "CT-1741234567890-1",
    "sessionId": "uuid",
    "userId": 1,
    "scorePercent": 80.0,
    "learnerName": "john",
    "videoTitle": "Spring Boot Tutorial",
    "videoId": "abc123",
    "videoUrl": "https://www.youtube.com/watch?v=abc123",
    "videoDuration": "12m 30s",
    "engagementScore": 0.92,
    "quizScore": 0.85,
    "engagementThreshold": 0.85,
    "quizThreshold": 0.80,
    "platformName": "CertifyTube",
    "platformAttribution": "Verification Layer 1 & 2",
    "status": "ACTIVE",
    "valid": true,
    "verificationToken": "abc123def456...",
    "verificationLink": "http://localhost:8080/api/certificates/verify/abc123def456...",
    "createdAtUtc": "2026-03-10T18:30:00Z"
  }
```

**New fields explained:**
| Field | Type | Description |
|-------|------|-------------|
| `status` | String | `"ACTIVE"` or `"REVOKED"` |
| `valid` | boolean | `true` if status is ACTIVE, `false` if REVOKED |
| `videoDuration` | String | Human-readable format: `"12m 30s"`, `"1h 05m 30s"`, or `"N/A"` |
| `engagementThreshold` | Double | Threshold at time of issuance (e.g. `0.85`) |
| `quizThreshold` | Double | Threshold at time of issuance (e.g. `0.80`) |

### Download PDF
```
GET /api/certificates/<certificateId>/pdf
Ã¢â€ â€™ application/pdf binary
```
Open in new tab or trigger download:
```js
const res = await fetch(`/api/certificates/${certId}/pdf`, { headers: { Authorization: `Bearer ${token}` } });
const blob = await res.blob();
window.open(URL.createObjectURL(blob));
```

### Delete Certificate (owner only)
```
DELETE /api/certificates/<certificateId>
Ã¢â€ â€™ { "message": "Certificate deleted successfully" }
```
Use a confirmation dialog before delete. On success, remove certificate from UI and navigate away from certificate detail page if open.

### Public Verify (anyone Ã¢â‚¬â€ NO auth required)
```
GET /api/certificates/verify/<verificationToken>
Ã¢â€ â€™ Same shape as above, but userId is null (privacy)
```

**Frontend verification page (`/verify/:token`):**

The verification page is public and should display:

| Field | Display |
|-------|---------|
| `valid` | Show **Ã¢Å“â€¦ Valid Certificate** or **Ã¢ÂÅ’ Revoked Certificate** banner |
| `status` | Badge: green "ACTIVE" or red "REVOKED" |
| `learnerName` | Learner's name |
| `videoTitle` | Course / video title |
| `videoDuration` | Duration of the video watched |
| `engagementScore` | Show as percentage (Ãƒâ€” 100): e.g. `92%` |
| `quizScore` | Show as percentage (Ãƒâ€” 100): e.g. `85%` |
| `engagementThreshold` | Show as percentage: e.g. `Required: 85%` |
| `quizThreshold` | Show as percentage: e.g. `Required: 80%` |
| `certificateNumber` | Certificate number |
| `createdAtUtc` | Issue date (format nicely) |
| `platformName` | "CertifyTube" |
| `videoUrl` | Link to the YouTube video |

**Invalid certificate (token not found):**
- Backend returns `400` with `{ "message": "Invalid certificate link" }`
- Show a clear **"Certificate Not Found"** error page

**Revoked certificate (token found but revoked):**
- Backend returns `200` with `valid: false, status: "REVOKED"`
- Show the certificate data but with a prominent **"This certificate has been revoked"** warning

### Certificate UI (owner's view)

The certificate detail page should display:
- All fields from the response above
- **Download PDF** button Ã¢â€ â€™ `GET /api/certificates/{id}/pdf`
- **Share** button Ã¢â€ â€™ copy `verificationLink` to clipboard
- **QR Code** Ã¢â€ â€™ generate from `verificationLink` using a JS QR library (e.g. `qrcode.react`)

---

## 7. Admin Panel (ADMIN role only)

All endpoints require `ROLE_ADMIN`. Any non-admin user gets `403`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/learners` | List learners with summary counts |
| GET | `/api/admin/learners/{learnerId}/profile?searchLimit=30` | Deep profile (sessions, engagement, quizzes, certificates, search cache) |
| DELETE | `/api/admin/certificates/{id}` | Delete certificate |
| POST | `/api/admin/certificates/{id}/revoke` | Revoke certificate |
| POST | `/api/admin/certificates/{id}/activate` | Reactivate revoked certificate |

### Certificate Status Actions
```
POST /api/admin/certificates/<certificateId>/revoke
-> { "message": "Certificate revoked successfully", "certificateId": "...", "status": "REVOKED" }

POST /api/admin/certificates/<certificateId>/activate
-> { "message": "Certificate activated successfully", "certificateId": "...", "status": "ACTIVE" }
```

---

## 8. Recommended Page Structure

```
/                    Ã¢â€ â€™ Search page (public)
/login               Ã¢â€ â€™ Login form
/signup              Ã¢â€ â€™ Signup form
/home                Ã¢â€ â€™ Dashboard (Continue Watching + My Learnings)
/watch/:videoId      Ã¢â€ â€™ Video player + event tracking
/analyze/:sessionId  Ã¢â€ â€™ Engagement results
/quiz/:quizId        Ã¢â€ â€™ Quiz UI
/result/:quizId      Ã¢â€ â€™ Quiz result + certificate download
/verify/:token       Ã¢â€ â€™ Public certificate verification
/admin               Ã¢â€ â€™ Admin dashboard (ADMIN only)
```

---

## 9. Complete User Flow (Sequence)

```
1. User signs up or logs in              Ã¢â€ â€™ store JWT
2. Home page loads                       Ã¢â€ â€™ GET /api/dashboard?status=ACTIVE (Continue Watching)
3. User searches for a video             Ã¢â€ â€™ GET /api/youtube/search
4. User clicks a video                   Ã¢â€ â€™ POST /api/sessions/start
   - If resumed: seek to lastPositionSec
   - If !stemEligible: show warning banner
5. Video plays, events stream            Ã¢â€ â€™ POST /api/events/batch (every 5-10s)
6. Video ends or user navigates away     Ã¢â€ â€™ flush events, POST /api/sessions/end
7. User clicks "Analyze" (STEM only)     Ã¢â€ â€™ POST /api/sessions/{id}/analyze
8. Show engagement results               Ã¢â€ â€™ display score, status, explanation
9. If ENGAGED Ã¢â€ â€™ show "Take Quiz"         Ã¢â€ â€™ GET /api/quiz/eligibility
10. User generates quiz                  Ã¢â€ â€™ POST /api/quiz/generate
11. User answers questions               Ã¢â€ â€™ POST /api/quiz/{id}/submit
12. If passed Ã¢â€ â€™ show certificate         Ã¢â€ â€™ GET /api/certificates/{id}/pdf
13. Share verification link              Ã¢â€ â€™ GET /api/certificates/verify/{token}

My Learnings page:
14. Load history                         Ã¢â€ â€™ GET /api/dashboard?status=COMPLETED,QUIZ_PENDING,CERTIFIED
15. Click pending video                  Ã¢â€ â€™ resume session or take next action
```

---

## 10. Error Handling

| Status | Meaning | Frontend Action |
|--------|---------|-----------------| 
| `400` | Validation error | Show error message |
| `401` | Not authenticated | Clear token Ã¢â€ â€™ redirect to login |
| `403` | Forbidden (wrong role/ownership) | Show "Access denied" |
| `500` | Server error | Show generic error |

All error responses follow: `{ "status": 4xx, "error": "...", "message": "..." }`

---

## 11. STEM Eligibility

Only videos in YouTube categories **26** (How-to & Style), **27** (Education), and **28** (Science & Technology) are STEM-eligible.

**Keyword Fallback:**  
Because many YouTube creators miscategorize their coding and tech tutorials under "People & Blogs" or "Entertainment", the backend also applies a **Keyword Fallback** on the video title and description.
If a video contains STEM keywords (e.g., *oop, python, machine learning, calculus, developer, docker*), it will be flagged as `stemEligible = true` regardless of its official YouTube category.

For non-STEM videos:
- Ã¢ÂÅ’ No engagement analysis
- Ã¢ÂÅ’ No quiz generation
- Ã¢ÂÅ’ No certificate
- Ã¢Å“â€¦ User can still watch the video

The `stemEligible` flag is returned in:
- `POST /api/sessions/start` Ã¢â€ â€™ `stemEligible` + `stemMessage`
- `GET /api/quiz/eligibility` Ã¢â€ â€™ `stemEligible`
- `GET /api/dashboard` Ã¢â€ â€™ each video item has `stemEligible`

