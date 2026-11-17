# ApexTrade Companion Production Server & Cloud Architecture Blueprint

This document defines the companion server-side infrastructure (Cloud Run / Node.js-TypeScript + PostgreSQL) designed to mirror and scale the authoritative Firestore collections already running live in the ApexTrade Android application.

## 1. Architecture Overview
- **Mobile Client (Implemented)**: Kotlin + Jetpack Compose Android platform authenticated via Google Sign-In (`CredentialManager` -> Firebase Auth) + Firebase App Check (Play Integrity / reCAPTCHA / Debug) + Vertex AI via Firebase AI Logic (`gemini-3.8-flash`).
- **Real-Time State & Rules (Implemented)**: Cloud Firestore (`default` / named DB) with zero-trust rules enforcing role isolation, immutable `ledger_entries` and `audit_logs`, and Maker-Checker (`checkerUid != makerUid`) dual control.
- **Companion Settlement & Analytics Worker (PostgreSQL Schema Included)**: Event-driven Cloud Run service syncing Firestore CDC events into PostgreSQL (`001_enterprise_brokerage_schema.sql`) for regulatory archiving, FIX/exchange routing, batch tax reporting (1099/CRS), and dead-letter Resend webhook ingestion.

## 2. REST / gRPC Companion Endpoints
| Endpoint | Auth Scope | Purpose |
|---|---|---|
| `POST /v1/webhooks/resend` | Resend HMAC Signature | Updates `email_jobs` delivery/bounce state & writes `email_suppressions` on hard bounce |
| `POST /v1/reports/statement-pdf` | Firebase ID Token (`uid`) | Generates signed regulatory monthly PDF + CSV account statements |
| `POST /v1/admin/reconcile` | Admin Custom Claim / `/admins/{uid}` | Runs cross-account trial balance verification (`SUM(credits) - SUM(debits) == cash_balance_usd`) |
| `POST /v1/compliance/sanctions-screen` | Internal Service Account | Screens submitted KYC entities against OFAC/EU/UN sanctions lists |

## 3. Environment Secrets
Managed securely via **AI Studio Secrets** / Google Cloud Secret Manager:
- `RESEND_API_KEY`: Production Resend API key for `https://api.resend.com/emails`
- `RESEND_FROM_EMAIL`: Verified domain sender (default `onboarding@resend.dev`)
- `DATABASE_URL`: Cloud SQL PostgreSQL connection string for companion worker
