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

4. `GET /api/certificates/verify/{token}`  
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

3. `POST /api/sessions/start`  
Request:
```json
{"videoId":"abc123","videoTitle":"Video title"}
```
Response:
```json
{"sessionId":"uuid"}
```
Notes:
- `user_id` is derived from JWT on backend.
- Canonical JSON is camelCase (`videoId`, `videoTitle`).
- Backward-compatible snake_case input aliases are supported (`video_id`, `video_title`).
- Backward-compatible alias exists: `POST /start_session`.

4. `POST /api/events/batch`  
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

5. `POST /api/sessions/{sessionId}/analyze?model=xgboost`  
Response:
```json
{
  "sessionId":"uuid",
  "model":"xgboost",
  "engagementScore":0.92,
  "threshold":0.85,
  "status":"ENGAGED",
  "explanation":"The primary factors influencing this score were...",
  "topPositive":[
    {"feature":"watch_time_ratio","shap_value":0.45,"feature_value":0.837,"behavior_category":"coverage"}
  ],
  "topNegative":[
    {"feature":"num_buffering_events","shap_value":-0.03,"feature_value":3.0,"behavior_category":"playback_quality"}
  ]
}
```
Notes:
- Backend enforces session ownership (`session.userId == jwt.userId`).
- Session must be ended before analyze:
  - call `POST /api/sessions/end?sessionId=<sessionId>` first.
- `model` query param is optional. Default: `xgboost`. Valid: `xgboost`, `ebm`.
- `engagementScore` is 0.0–1.0 (probability).
- `status` is `"ENGAGED"` or `"NOT_ENGAGED"` (backend decision).
- For XGBoost: contributors have `shap_value`.
- For EBM: contributors have `contribution`.

6. `GET /api/quiz/eligibility?sessionId={sessionId}`  
Response:
```json
{
  "sessionId":"uuid",
  "eligible":true,
  "reason":"Eligible",
  "requiredEngagementScore":85.0,
  "latestEngagementScore":88.4,
  "engagementPassed":true,
  "maxFailedAttempts":3,
  "failedAttemptsUsed":1,
  "remainingAttempts":2
}
```
Notes:
- Backend enforces session ownership (`session.userId == jwt.userId`).

7. `POST /api/quiz/generate`  
Frontend must use this canonical path only.  
Legacy aliases exist in backend for backward compatibility only (`/api/quiz/genrate`, `/api/quizz/generate`, `/api/quizz/genrate`).  
Request:
```json
{"sessionId":"uuid","difficulty":"medium","transcript":"optional"}
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
- Backend enforces session ownership (`session.userId == jwt.userId`).

8. `GET /api/quiz/{quizId}`  
Response: same shape as generate response.
Notes:
- Backend enforces quiz ownership (`quiz.userId == jwt.userId`).

9. `POST /api/quiz/{quizId}/submit`  
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

10. `GET /api/quiz/{quizId}/result`  
Response: same shape as submit response.
Notes:
- Backend enforces quiz ownership (`quiz.userId == jwt.userId`).

11. `GET /api/certificates/{certificateId}`  
Response: certificate metadata.
Notes:
- Owner-only endpoint (`certificate.userId == jwt.userId`).

12. `GET /api/certificates/{certificateId}/pdf`  
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
