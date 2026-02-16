# BiasharaHub Backend

Spring Boot REST API for the BiasharaHub multi-tenant SME commerce platform.

**@ [Sysnova Technologies](https://sysnovatechnologies.com).**

**This repository is backend-only.** The frontend lives in a separate project (e.g. **BisharaHubFrontend**) and must run on its own. The two interact **only via HTTP API calls**: frontend calls this API (local: `http://localhost:5050/api`; UAT: `https://biasharahub-api-test.sysnovatechnologies.com/api`; prod: `https://biasharahub-api.sysnovatechnologies.com/api`). CORS allows localhost and the UAT/prod frontend origins.

## Tech Stack

- **Java 17** / **Spring Boot 3.2**
- **PostgreSQL** with schema-per-tenant isolation
- **Liquibase** for database migrations
- **JWT** for authentication
- **Spring Security**

## Prerequisites

- Java 17+
- PostgreSQL 14+
- Maven 3.8+

## Setup

1. **Create the database** (use the database name from your environment):
   - **Local:** Create a database (e.g. `biasharahub`) manually: `psql -U postgres -c "CREATE DATABASE biasharahub;"`
   - **UAT/Prod:** Use the database configured in your environment (e.g. `biasharahub_test`, `biasharahub`). Set `DB_URL` accordingly.

2. **Set default schema (one-time, optional):**  
   See `src/main/resources/db/scripts/set-default-schema.sql`. Replace `YOUR_DATABASE` with your database name and run the `ALTER DATABASE` statement.

3. **Configure environment:**
   ```bash
   # Required: DB_URL must point to your database (used in UAT/Prod; defaults to localhost/biasharahub for local)
   # Windows (PowerShell)
   $env:DB_URL="jdbc:postgresql://localhost:5432/biasharahub"
   $env:DB_USERNAME="postgres"
   $env:DB_PASSWORD="your_postgres_password"

   # Linux/Mac
   export DB_URL="jdbc:postgresql://localhost:5432/biasharahub"
   export DB_USERNAME="postgres"
   export DB_PASSWORD="your_postgres_password"
   ```

4. **Add logo and favicon:**
   Copy `logo.png` and `favicon.png` to `src/main/resources/static/`
   (Uses BiasharaHub logo: dark green briefcase with bar chart, orange location pin)

5. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

6. **API base URL:** Local `http://localhost:5050/api`; UAT `https://biasharahub-api-test.sysnovatechnologies.com/api`; prod `https://biasharahub-api.sysnovatechnologies.com/api`

## Multi-Tenancy (Schema per Tenant)

- Each tenant has an isolated PostgreSQL schema (e.g., `tenant_default`)
- Set `X-Tenant-ID` header with tenant UUID to switch tenant context
- Default tenant: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

## User roles and who can add users

| Role | Who can add | How |
|------|-------------|-----|
| **Customer** | Self only | `POST /auth/register` (name, email, password). 2FA is always on: a code is sent by email; complete login with `POST /auth/verify-code`. |
| **Owner** | Platform admin only | `POST /admin/owners` (super_admin). Body: `{"name","email"}`. Temp password by email; owner can enable/disable 2FA. |
| **Staff** | Owner only | `POST /users/staff` (owner). Body: `{"name","email"}`. Temp password by email. 2FA is always on and cannot be disabled. |
| **Assistant admin** | Platform admin only | `POST /admin/assistant-admins` (super_admin). Body: `{"name","email"}`. Temp password by email. 2FA is always on and cannot be disabled. |

- **Change password:** Any authenticated user can change their password via `POST /auth/change-password` (body: `{"currentPassword","newPassword"}`). Owners, staff, and assistant admins should use this after first login to replace the temporary password.
- **2FA:** For **customers**, **staff**, and **assistant_admin**, 2FA is always on and cannot be enabled or disabled (calling enable/disable returns 403). Only **owner** and **super_admin** can enable or disable 2FA for themselves via `POST /auth/2fa/enable` and `POST /auth/2fa/disable`.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register as customer (name, email, password). Returns requiresTwoFactor; verify with POST /auth/verify-code. |
| POST | `/auth/login` | Login (email + password) |
| POST | `/auth/change-password` | Change password (auth required; body: currentPassword, newPassword) |
| POST | `/auth/refresh` | Refresh access token (body: `{"refreshToken":"..."}`) |
| POST | `/auth/verify-code` | Verify 2FA code (email + code) to complete login when 2FA is required |
| POST | `/auth/2fa/enable` | Enable 2FA (owner/super_admin only; customer/staff/assistant_admin get 403) |
| POST | `/auth/2fa/disable` | Disable 2FA (owner/super_admin only; customer/staff/assistant_admin get 403) |
| GET | `/auth/2fa/oauth/authorize?stateToken=...` | Start OAuth2 2FA (redirects to Google) |
| GET | `/auth/oauth2/callback?code=...&state=...` | OAuth2 callback (redirects to frontend with tokens) |
| POST | `/admin/owners` | Add owner (super_admin only; body: name, email) |
| POST | `/admin/assistant-admins` | Add assistant admin (super_admin only; body: name, email) |
| POST | `/users/staff` | Add staff (owner only; body: name, email) |
| GET | `/users/staff` | List staff (owner only) |
| GET | `/products/categories` | List product categories for frontend dropdown (no auth) |
| GET | `/products/businesses` | List businesses/owners for customer filter dropdown (no auth) |
| GET | `/products` | List products (owner/staff: only their business; customers: all, optional filters). **Customer filters:** `category`, `businessId`, `businessName`, `ownerId` (no auth for GET) |
| GET | `/products/{id}` | Get product (owner/staff: only their business) |
| POST | `/products` | Create product (owner/staff; scoped to their business) |
| PUT | `/products/{id}` | Update product (owner/staff; only their business) |
| DELETE | `/products/{id}` | Delete product (owner/staff; only their business) |
| POST | `/products/upload-image` | Upload product image to R2 (owner/staff; body: multipart `file`) |
| GET | `/orders` | List orders (customer: own orders; owner/staff: orders containing their business products) |
| GET | `/orders/{id}` | Get order (auth; customer or owner/staff with access) |
| POST | `/orders` | Create order (auth; body: `CreateOrderRequest` with `items` and optional `shippingAddress`). Validates stock; deducts inventory; creates pending payment. |
| PATCH | `/orders/{id}/status` | Update order status (owner/staff; query `status`) |
| POST | `/orders/{orderId}/payments/initiate` | Initiate payment for order (M-PESA stub; auth) |
| PATCH | `/orders/{orderId}/payments/{paymentId}/confirm` | Confirm payment (stub for M-PESA callback; auth) |
| GET | `/shipments` | List shipments (auth required) |
| GET | `/analytics` | Dashboard analytics (owner/staff) |
| GET | `/public/tenants/{id}/branding` | Tenant branding (logo, colors) |

**Auth:** Access token expires in **1 hour**. Use the `refreshToken` from login/register and `POST /auth/refresh` with body `{"refreshToken":"<refreshToken>"}` to get a new `token` and `refreshToken` without re-login.

### Product images (Cloudflare R2)

Product images are stored in **Cloudflare R2** (S3-compatible). To enable uploads, set:

| Variable | Description |
|----------|-------------|
| `R2_ENABLED` | `true` to enable R2 |
| `R2_BUCKET` | Bucket name (default `biasharahub-products`) |
| `R2_ENDPOINT` | R2 endpoint, e.g. `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `R2_ACCESS_KEY_ID` | R2 Access Key ID |
| `R2_SECRET_ACCESS_KEY` | R2 Secret Access Key |
| `R2_PUBLIC_URL` | Public URL base for images (e.g. R2 public bucket URL or custom domain) |

Flow: upload image with `POST /products/upload-image` (multipart `file`), use the returned `url` in the product's `images` array when creating or updating a product.

### M-Pesa (Daraja) STK Push

Payments use **M-Pesa STK Push**. You must use **your own** app credentials from the [Daraja portal](https://developer.safaricom.co.ke): create an app, add **Lipa Na M-Pesa Online** as a product, and use the **shortcode**, **passkey**, **consumer key**, and **consumer secret** from that app. The error **"Merchant does not exist" (500.001.1001)** means the shortcode or credentials are wrong or not from your Daraja app—replace them with the values from the portal.

The **callback URL** (where Safaricom sends the payment result) must be:

- **HTTPS** (not `http://`)
- **Publicly reachable** (Safaricom’s servers must be able to POST to it; `localhost` is not valid)

**Local development:** Expose your backend with a tunnel, then set the callback URL to that public HTTPS URL.

1. Start a tunnel, e.g. **ngrok**: `ngrok http 5050`
2. Copy the HTTPS URL (e.g. `https://abc123.ngrok-free.app`)
3. Set the callback URL when starting the app:
   - **Windows (PowerShell):** `$env:MPESA_STK_CALLBACK_URL="https://abc123.ngrok-free.app/api/payments/mpesa/stk-callback"`
   - **Linux/macOS:** `export MPESA_STK_CALLBACK_URL="https://abc123.ngrok-free.app/api/payments/mpesa/stk-callback"`
4. Or put the same value in `application-local.yml` under `app.mpesa.stk-callback-url`.

If the callback is not reachable (e.g. you skip the tunnel), the STK push may still be sent to the phone; the user can complete payment and the app’s **“I’ve paid”** button can be used to confirm via `PATCH /orders/{id}/payments/{paymentId}/confirm`.

### Kafka (order / payment events)

Order and payment events can be published to **Kafka** for async processing (notifications, shipments, analytics). Kafka is **off by default** so the app starts without a broker.

| Variable | Description |
|----------|-------------|
| `KAFKA_ENABLED` | `true` to enable Kafka (default `false`) |
| `KAFKA_BOOTSTRAP_SERVERS` | Broker list, e.g. `localhost:9092` |
| `KAFKA_TOPIC_ORDER_CREATED` | Topic for order created (default `orders.created`) |
| `KAFKA_TOPIC_PAYMENT_COMPLETED` | Topic for payment completed (default `payments.completed`) |
| `KAFKA_CONSUMER_GROUP` | Consumer group for listeners (default `biasharahub`) |

When enabled, the app publishes to Kafka when an order is created and when a payment is confirmed. Example consumers log events; see `OrderEventKafkaListener` and `docs/MESSAGING.md`.

**Without Kafka:** Set `MESSAGING_IN_PROCESS_ENABLED=true` to run order/payment handlers in a background thread (no broker). See `docs/MESSAGING.md` for in-process async, DB outbox, and other options.

## Two-Factor Authentication (2FA) via OAuth 2.0

2FA uses **Google sign-in** as the second factor after password. All configuration is on the backend; the frontend only calls APIs and redirects.

### 1. Configure Google OAuth2 (backend)

1. In [Google Cloud Console](https://console.cloud.google.com/) create (or use) a project and enable the **Google+ API** (or **Google Identity**).
2. Under **APIs & Services → Credentials**, create an **OAuth 2.0 Client ID** (type **Web application**).
3. Set **Authorized redirect URIs** to your backend callback URL, e.g.:
   - Local: `http://localhost:5050/api/auth/oauth2/callback`; UAT: `https://biasharahub-api-test.sysnovatechnologies.com/api/auth/oauth2/callback`; prod: `https://biasharahub-api.sysnovatechnologies.com/api/auth/oauth2/callback`
   - Production: `https://your-api.example.com/api/auth/oauth2/callback`
4. Set environment variables (or `application.yml`):

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` | OAuth 2.0 client ID from Google |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 client secret |
| `OAUTH2_BACKEND_BASE_URL` | Backend base URL (local default `http://localhost:5050/api`; test/prod use application-test.yml / application-prod.yml) |
| `OAUTH2_FRONTEND_REDIRECT_URI` | Frontend URL after OAuth (local default `http://localhost:3000/auth/callback`; UAT/prod in profile yml) |

**Example (PowerShell):**
```powershell
$env:GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"
$env:OAUTH2_FRONTEND_REDIRECT_URI="http://localhost:3000/auth/callback"
```

### 2. Enable or disable 2FA (API only)

- **Enable 2FA:** `POST /api/auth/2fa/enable` with header `Authorization: Bearer <access token>`.
- **Disable 2FA:** `POST /api/auth/2fa/disable` with header `Authorization: Bearer <access token>`.

### 3. Flow (backend supports this)

- **Step 1 – Login:** `POST /api/auth/login` with `{"email":"...","password":"..."}`.
  - If the user has 2FA enabled, response: `requiresTwoFactor: true`, `stateToken`, `authorizationUrl`, `user` (no `token`/`refreshToken`).
- **Step 2 – OAuth2:** Frontend redirects the user to `authorizationUrl` (or to `GET /api/auth/2fa/oauth/authorize?stateToken=<stateToken>`). User signs in with Google.
- **Step 3 – Callback:** Google redirects back to the backend; backend exchanges the code, verifies the Google email matches the user, issues JWTs, and redirects to the frontend with tokens in the URL hash: `#access_token=...&refresh_token=...`. On error, redirects with `?error=invalid`.

### 4. Frontend

- After `POST /api/auth/login`, if `requiresTwoFactor === true`:
  - Either redirect the user to `response.authorizationUrl`, or to `GET /api/auth/2fa/oauth/authorize?stateToken=<response.stateToken>`.
- Provide a page at the `OAUTH2_FRONTEND_REDIRECT_URI` path (e.g. `/auth/callback`) that:
  - Reads `window.location.hash` for `access_token` and `refresh_token` (or `window.location.search` for `error=invalid`).
  - Stores the tokens and continues as logged in (or shows an error).

## Production email (REST API only – no SMTP)

All transactional and 2FA email is sent via **REST APIs only** (no SMTP):

- **Postmark** (recommended): `POST https://api.postmarkapp.com/email` with server token.
- **Gmail API**: OAuth2 + `users.messages.send` (no SMTP / app passwords).

The `spring.mail.*` settings in `application.yml` are not used for sending.

## Production email (Postmark) – recommended

For production, use **Postmark** (REST API, simple server token).

### Configure Postmark

- Set environment variables:
  - `POSTMARK_SERVER_TOKEN`
  - `MAIL_FROM` (a verified sender in Postmark)
  - Optional: `POSTMARK_MESSAGE_STREAM` (e.g. `outbound`)
  - You can use the `.env.postmark.example` file as a template (copy to `.env` or `.env.local` and fill in real values – those files are gitignored).

When `POSTMARK_SERVER_TOKEN` is set, the backend will send **2FA codes and transactional emails via Postmark**.
If it is not set, the backend falls back to **Gmail OAuth** (if configured), otherwise a no-op sender.

### Features

- **Full Spring Boot Gmail OAuth MailSender** – sends via Gmail API with OAuth2 (no app passwords).
- **Automatic refresh token scheduler** – access token is refreshed every 50 minutes so sends never block on expiry.
- **Multi-tenant** – `MultiTenantEmailSender` resolves tenant from `TenantContext` and delegates to the default Gmail OAuth sender (extend with per-tenant config if needed).

### Gmail OAuth setup

1. In [Google Cloud Console](https://console.cloud.google.com/): create a project, enable **Gmail API**, create **OAuth 2.0 Client ID** (Desktop or Web), and obtain a **refresh token** (e.g. via OAuth2 Playground: authorize with `https://www.googleapis.com/auth/gmail.send`, exchange code for tokens, copy refresh token).
2. Configure credentials using **either** of these:

   **Option A – Local config file (recommended for dev)**  
   Copy `src/main/resources/application-local.example.yml` to `application-local.yml` in the same folder, fill in your Gmail OAuth values, and run with the **dev** profile (e.g. `mvn spring-boot:run -Dspring-boot.run.profiles=dev`). The dev profile includes `local`, so `application-local.yml` is loaded automatically if present. The file is gitignored so secrets are not committed.

   **Option B – Environment variables**

| Variable | Description |
|----------|-------------|
| `GMAIL_CLIENT_ID` | OAuth 2.0 client ID |
| `GMAIL_CLIENT_SECRET` | OAuth 2.0 client secret |
| `GMAIL_REFRESH_TOKEN` | Long-lived refresh token (from consent flow) |
| `MAIL_FROM` | Sender address (e.g. `no-reply@yourdomain.com`) |

3. Optional: `app.mail.gmail.refresh-interval-ms` (default 3000000 = 50 min).

If Gmail OAuth is not configured, the Gmail sender is disabled and a no-op sender is used (no errors, emails are not sent).

### 2FA email service

`MailService.sendTwoFactorCode(toEmail, code)` uses the configured `EmailSender` (Postmark or Gmail API via REST; no SMTP). Same pipeline for password reset and welcome emails.

## Demo Users (tenant_default)

Use password **`password123`** for all demo accounts. (Omit `X-Tenant-ID` or use `a1b2c3d4-e5f6-7890-abcd-ef1234567890`.)

| Email | Password | Role |
|-------|----------|------|
| admin@biashara.com | password123 | super_admin |
| owner@biashara.com | password123 | owner |
| staff@biashara.com | password123 | staff |
| customer@biashara.com | password123 | customer |

## Static assets (API-only)

The backend serves only minimal static assets used by the API (e.g. tenant branding): **`/static/logo.png`**, **`/static/favicon.png`**. No frontend app or PWA is served from this repo; the frontend is a separate application that calls this API.

## Migrations (Liquibase)

Migrations are in `src/main/resources/db/changelog/`:

- **001**: `public.tenants` table
- **002**: `create_tenant_schema()` function
- **003**: Default tenant + schema creation
- **004**: Demo users seed

Liquibase runs automatically on application startup.

## License

Proprietary - BiasharaHub. @ Sysnova Technologies.
