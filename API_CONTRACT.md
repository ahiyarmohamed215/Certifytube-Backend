# Frontend Integration Contract

**Base**
- Base URL: `http://localhost:8080`
- Auth header for protected APIs: `Authorization: Bearer <JWT>`

## Public APIs
1. `POST /api/auth/signup`  
Request:
```json
{"email":"user@test.com","password":"Password@123"}
```
Response:
```json
{"userId":1,"email":"user@test.com","role":"USER","token":"...","tokenType":"Bearer"}
```

2. `POST /api/auth/login`  
Request:
```json
{"email":"user@test.com","password":"Password@123"}
```
Response: same as signup.

3. `GET /api/youtube/search?q=spring boot&limit=20`  
Response:
```json
{"query":"spring boot","count":20,"videos":[{"videoId":"...","title":"...","iframeUrl":"https://www.youtube.com/embed/..."}]}
```

4. `GET /api/youtube/transcript?videoId={videoId}`  
Response:
```json
{
  "videoId":"dQw4w9WgXcQ",
  "transcript":"cleaned transcript text ...",
  "transcriptLength":3560,
  "fromCache":false,
  "cachedAtUtc":"2026-02-21T15:00:00Z"
}
```

5. `GET /api/certificates/verify/{token}`  
Response:
```json
{"certificateId":"...","certificateNumber":"...","sessionId":"...","userId":1,"scorePercent":84.0,"verificationToken":"...","verificationLink":"...","createdAtUtc":"..."}
```
Notes:
- Public verify currently returns `userId` for demo/prototype transparency.
- For production/privacy hardening, replace `userId` with masked identity.

## Protected APIs
1. `GET /api/auth/me`  
Response:
```json
{"userId":1,"email":"user@test.com","role":"USER"}
```

2. `POST /api/auth/logout` (or `/api/auth/signout`)  
Response:
```json
{"message":"Logged out"}
```
Notes:
- Protected endpoint (JWT required).
- Current JWT is revoked server-side by `jti` until token expiry.

3. `GET /api/dashboard` or `GET /api/dashboard?status=ACTIVE` or `GET /api/dashboard?status=COMPLETED,QUIZ_PENDING,CERTIFIED`  
Response:
```json
{
  "activeVideos": [{"sessionId":"uuid","videoId":"abc123","videoTitle":"...","thumbnailUrl":"...","lastPositionSec":120.5,"videoDurationSec":600.0,"progressPercent":20.08,"status":"ACTIVE","stemEligible":true,"engagementScore":null,"certificateId":null,"createdAt":"2026-03-04T15:30:00Z"}],
  "completedVideos": [...],
  "quizPendingVideos": [...],
  "certifiedVideos": [...]
}
```
Notes:
- Optional `status` query param (comma-separated): filter by session status.
- If omitted, all statuses returned.
- Statuses: `ACTIVE`, `COMPLETED`, `QUIZ_PENDING`, `CERTIFIED`.
- Frontend: Home page → `?status=ACTIVE`, My Learnings → `?status=COMPLETED,QUIZ_PENDING,CERTIFIED`.

4. `POST /api/sessions/start`  
Request:
```json
{"videoId":"abc123","videoTitle":"Video title"}
```
Response:
```json
{"sessionId":"uuid","resumed":false,"lastPositionSec":null,"videoDurationSec":null,"stemEligible":true,"stemMessage":null}
```
Notes:
- `user_id` is derived from JWT on backend.
- `resumed`: true if returning to same video (existing open session found).
- `lastPositionSec`: if resumed, frontend should seek video to this position.
- `stemEligible`: true if video is STEM (YouTube category 27 or 28). Non-STEM videos cannot be analyzed/quizzed/certified.
- `stemMessage`: warning text for non-STEM videos.
- Backward-compatible alias exists: `POST /start_session`.

5. `POST /api/events/batch`  
Request:
```json
[
  {
    "sessionId":"uuid",
    "eventType":"play",
    "playerState":1,
    "playbackRate":1.0,
    "currentTimeSec":10.2,
    "videoDurationSec":300.0,
    "clientCreatedAtLocal":"2026-02-13T20:00:00",
    "clientTzOffsetMin":-330,
    "clientEventMs":123456,
    "seekFromSec":null,
    "seekToSec":null
  }
]
```
Response:
```json
{
  "saved":18,
  "rejected":2,
  "errors":[
    {"index":3,"message":"eventType is required"},
    {"index":9,"message":"Session already ended"}
  ]
}
```
Notes:
- Do **not** send `userId` from frontend.
- Backend derives user identity from JWT and derives video metadata from `sessionId`.
- Backend enforces session ownership (`session.userId == jwt.userId`) for each event batch.
- If ownership check fails for any event/session in the request:
  - backend rejects the whole request with `403`
  - batch is not processed (`saved/rejected/errors` payload is not returned).
- If ownership check passes:
  - backend validates/processes events and returns `saved/rejected/errors`.
- Keep payload minimal for frontend:
  - do not send `videoTitle`
  - send `videoDurationSec` only when available (first/ended checkpoints preferred).
- `clientEventMs` must be monotonic:
  - use `performance.now()` (not `Date.now()`).

6. `POST /api/sessions/{sessionId}/analyze?model=xgboost`  
Response:
```json
{
  "sessionId":"uuid",
  "model":"xgboost",
  "engagementScore":0.92,
  "threshold":0.85,
  "status":"ENGAGED",
  "explanation":"The primary factors influencing this score were..."
}
```
Notes:
- **STEM only**: non-STEM videos return error.
- **Idempotent**: if called again within 60s, returns cached result.
- Session must be ended before analyze.
- `model` query param is optional. Default: `xgboost`. Valid: `xgboost`, `ebm`.
- If ENGAGED → session status moves to `QUIZ_PENDING`.

7. `GET /api/quiz/eligibility?sessionId={sessionId}`  
Response:
```json
{
  "sessionId":"uuid",
  "eligible":true,
  "reason":"Eligible",
  "requiredEngagementScore":0.85,
  "latestEngagementScore":0.92,
  "engagementPassed":true,
  "maxFailedAttempts":2,
  "failedAttemptsUsed":1,
  "remainingAttempts":1
}
```
Notes:
- Backend enforces session ownership.
- Response now includes `stemEligible` boolean.
- Non-STEM videos: `eligible: false, reason: "Only STEM-based skill videos are eligible"`.

8. `POST /api/quiz/generate`  
Request:
```json
{"sessionId":"uuid","difficulty":"medium","numQuestions":10,"includeCoding":false}
```
Response:
```json
{
  "quizId":"uuid",
  "sessionId":"uuid",
  "videoId":"abc123",
  "videoTitle":"Video title",
  "difficulty":"medium",
  "totalQuestions":10,
  "questions":[
    {"questionId":"q1","questionType":"mcq","questionText":"...","options":["A","B","C","D"]}
  ]
}
```
Notes:
- **No transcript needed** — ML server fetches/caches transcripts automatically.
- **Idempotent**: if called again within 60s, returns existing quiz.
- `numQuestions` and `includeCoding` are optional.
- **STEM only**: non-STEM videos return error.

9. `GET /api/quiz/{quizId}`  
Response: same shape as generate response.
Notes:
- Backend enforces quiz ownership (`quiz.userId == jwt.userId`).

10. `POST /api/quiz/{quizId}/submit`  
Request:
```json
{"answers":{"q1":"A","q2":"true","q3":"fill value"}}
```
Response:
```json
{
  "quizId":"uuid",
  "correctCount":8,
  "totalCount":10,
  "scorePercent":80.0,
  "passed":true,
  "certificateId":"cert-uuid",
  "verificationLink":"http://localhost:8080/api/certificates/verify/{token}"
}
```
Notes:
- Backend enforces quiz ownership (`quiz.userId == jwt.userId`).
- `answers` keys can be the exact `questionId` values from generate response; fallback `q1`, `q2`, ... is also accepted.
- Learner can retry after failing. After 2 failed attempts in the same engagement window, learner must rewatch + analyze again before next attempt.

11. `GET /api/quiz/{quizId}/result`  
Response: same shape as submit response.
Notes:
- Backend enforces quiz ownership (`quiz.userId == jwt.userId`).

12. `GET /api/certificates/{certificateId}`  
Response: certificate metadata.
Notes:
- Owner-only endpoint (`certificate.userId == jwt.userId`).

13. `GET /api/certificates/{certificateId}/pdf`  
Response: PDF bytes (`application/pdf`).
Notes:
- Owner-only endpoint (`certificate.userId == jwt.userId`).

## Auth/Navigation Rules for Frontend
1. Search/list page can be public.
2. Watch/start session/events/analyze/quiz/certificate pages must require login.
3. For stable scoring order:
   - flush final events batch
   - call session end endpoint
   - then call analyze.
4. Call `/api/auth/me`:
   - on app boot
   - after login/signup/logout actions
   - optionally when entering protected routes only.
5. For all protected API failures with `401`, clear token and redirect to login.

## Error Handling
- `400`: business/validation error (show message)
- `401`: not logged in (redirect login)
- `403`: forbidden
- `500`: generic server error

## Session End
- Preferred: `POST /api/sessions/end?sessionId=<sessionId>`
- Backward-compatible alias: `POST /end_session?sessionId=<sessionId>`
- Response:
```json
{"ended":true}
```
- Protected endpoint (JWT required).

## Logout Semantics
- JWT contains `jti` claim.
- `POST /api/auth/logout` stores revoked `jti` with token expiry in persistence.
- JWT filter checks revoked `jti` on each protected request and rejects revoked tokens.
- Backend cleanup job deletes expired revoked JTIs periodically (`auth.jwt.revoked-cleanup-ms`).
- Frontend must still clear locally stored token after logout response.
