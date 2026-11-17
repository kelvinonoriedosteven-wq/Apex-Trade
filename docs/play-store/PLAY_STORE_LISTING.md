# ApexTrade — Google Play Console Preparation Guide

## 1. Store Listing Metadata (Play Policy Compliant)
- **App Title (<= 30 chars)**: `ApexTrade` (9 chars)
- **Package Name (`applicationId`)**: `com.aistudio.apextrade.vxqrmz`
- **Current Version**: `versionName = "1.0"` | `versionCode = 1`
- **Minimum / Target SDK**: `minSdk = 24` (Android 7.0) | `targetSdk = 36` (Android 16)
- **Category**: Finance
- **Short Description (<= 80 chars)**:
  `Global stocks, ETFs, crypto, forex & commodities with live portfolio custody.`
- **Full Description**:
  ApexTrade gives individual investors and active traders unified mobile access to global equities, ETFs, market indices, commodities, bonds, futures, forex pairs, and 24/7 cryptocurrencies.

  Key Capabilities:
  - Worldwide Multi-Asset Markets: Search & trade US, European, UK, Asian, and global crypto markets with live charts and technical indicators.
  - Real-Time Portfolio Custody: Track available cash, cost basis, unrealized P&L, watchlist alerts, and immutable double-entry ledger entries.
  - Compliance & Manual Settlement Workflows: Integrated KYC identity submission, deposit proof uploads, and manual payout verification (24-hour review SLA).
  - Security & Operations: Google Sign-In authentication, optional local 2FA PIN lock, active device session management, in-app support tickets, and read-only AI portfolio insights.

## 2. Privacy & Permissions Audit (Play Policy Compliant)
- **Manifest Permissions Declared**:
  - `android.permission.INTERNET`
  - `android.permission.ACCESS_NETWORK_STATE`
- **Storage & Media Access**: Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are NOT requested). KYC IDs and deposit screenshots use Android's zero-permission `ActivityResultContracts.PickVisualMedia`.
- **Network Security**: `res/xml/network_security_config.xml` enforces HTTPS (`cleartextTrafficPermitted="false"`) for all external endpoints.

## 3. Google Play Data Safety Form Reference
| Data Category | Collected | Shared | Purpose |
|---|---|---|---|
| Name & Email (Google Sign-In) | Yes | No | Account creation, authentication, compliance notifications |
| Financial Info (Deposits, Withdrawals, Trades) | Yes | No | Core brokerage ledger, order execution, compliance audit |
| Photos (KYC ID / Deposit Proof via Photo Picker) | Yes | No | User-initiated KYC & deposit verification |
| App Activity & Device Session IDs | Yes | No | Session revocation security & diagnostics |

*Encryption*: All data transmitted via HTTPS/TLS; persisted in Cloud Firestore with zero-trust per-user security rules.

## 4. Generating Your Upload Keystore & Publishing Steps
1. **Generate permanent upload keystore (never commit to Git)**:
   ```bash
   keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **Register Upload Key SHA-1 & SHA-256 in Firebase Console**:
   Add the SHA-1 and SHA-256 fingerprints of `my-upload-key.jks` (and your Google Play App Signing key fingerprint once Play creates it) to Firebase project `gen-lang-client-0511066234` so Google Sign-In succeeds on release builds.
3. **Build Signed AAB**:
   - **Option A (GitHub Actions)**: Add `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` to GitHub Repository Secrets, then tag `v1.0.0`. Download `brokerage-android.aab` from the release artifacts.
   - **Option B (Local / AI Studio Export)**: Export project -> set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` -> run `gradle :app:bundleRelease`.
4. **Rollout Sequence in Google Play Console**:
   - **Step 1 — Internal Testing Track**: Upload `app-release.aab`, enroll up to 100 internal QA testers, verify Google Sign-In, KYC upload, deposit/withdrawal flows, and crash reports.
   - **Step 2 — Closed Testing Track**: Complete Play Console Financial Features declaration (if applicable in target jurisdictions), Data Safety form, Privacy Policy URL, store graphics (512x512 icon, 1024x500 feature graphic, phone screenshots), and run required closed testing period.
   - **Step 3 — Production Rollout**: Start staged rollout (e.g., 10% -> 50% -> 100%) only after Google Play review approval.
