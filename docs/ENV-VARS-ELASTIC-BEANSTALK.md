# Environment Variables for Elastic Beanstalk

Your `application.yml` uses placeholders like `${VAR_NAME:default}`. In Elastic Beanstalk you set **Name** = the variable name and **Value** = the value. Spring Boot reads them automatically.

---

## Where to set them in AWS

1. **Elastic Beanstalk** → your environment (e.g. **biasharahub-uat**)
2. **Configuration** → **Software** → **Edit**
3. Scroll to **Environment properties**
4. For each variable: **Name** = exact name below, **Value** = your value
5. Click **Apply**

---

## Required (UAT / any environment)

| Name | Example value | Used for |
|------|----------------|----------|
| `SPRING_PROFILES_ACTIVE` | `test` (UAT) or `prod` | Profile (test/prod) |
| `SERVER_PORT` | `5000` | Port EB proxy expects |
| `DB_URL` | `jdbc:postgresql://xxx.rds.amazonaws.com:5432/biasharahub_test` | Database URL |
| `DB_USERNAME` | `biasharaadmin` | DB user |
| `DB_PASSWORD` | (your RDS password) | DB password |
| `JWT_SECRET` | (e.g. from `openssl rand -base64 32`) | JWT signing |
| `APP_FRONTEND_URL` | `https://biasharahub-app-test.sysnovatechnologies.com` | Frontend URL (links, redirects) |

---

## CORS (so frontend can call API)

CORS is currently hardcoded in `SecurityConfig`. To allow your hosted frontend, either:

- Add the origin in code (e.g. `https://biasharahub-app-test.sysnovatechnologies.com` in `SecurityConfig`), or  
- Use an env-driven CORS config (e.g. `APP_CORS_ALLOWED_ORIGINS` in yml and in `SecurityConfig`).

For a quick fix, add this origin in `SecurityConfig.corsConfigurationSource()`:

`https://biasharahub-app-test.sysnovatechnologies.com`

---

## Optional – Mail (2FA / transactional)

| Name | Example | Notes |
|------|---------|--------|
| `MAIL_FROM` | `noreply@sysnovatechnologies.com` | Sender address |
| `POSTMARK_SERVER_TOKEN` | (token) | If using Postmark |
| `POSTMARK_MESSAGE_STREAM` | `outbound` | Optional |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | - | If using SMTP |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN` | - | If using Gmail OAuth |

---

## Optional – Redis (cache)

| Name | Example | Notes |
|------|---------|--------|
| `REDIS_HOST` | (ElastiCache endpoint) | Leave empty if no Redis |
| `REDIS_PORT` | `6379` | Default 6379 |

---

## Optional – M-Pesa (payments / payouts)

| Name | Example | Notes |
|------|---------|--------|
| `MPESA_ENABLED` | `true` | Turn on M-Pesa |
| `MPESA_ENV` | `sandbox` or `production` | |
| `MPESA_BASE_URL` | `https://sandbox.safaricom.co.ke` | |
| `MPESA_SHORTCODE` | (your shortcode) | |
| `MPESA_PASSKEY` | (from Daraja) | |
| `MPESA_CONSUMER_KEY` | (from Daraja) | |
| `MPESA_CONSUMER_SECRET` | (from Daraja) | |
| `MPESA_STK_CALLBACK_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api/payments/mpesa/stk-callback` | Must be HTTPS |
| `MPESA_B2C_CALLBACK_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api/payments/mpesa/b2c-callback` | |
| `MPESA_B2C_INITIATOR_NAME` | (from portal) | B2C payouts |
| `MPESA_B2C_SECURITY_CREDENTIAL` | (from portal) | B2C payouts |
| `MPESA_B2C_SHORTCODE` | (shortcode) | Optional; defaults to MPESA_SHORTCODE |
| `MPESA_TIMEOUT_MS` | `10000` | Optional |
| `MPESA_PAYOUT_PRODUCT_NAME` | `testapi` | Optional |

---

## Optional – WhatsApp (Twilio)

| Name | Example | Notes |
|------|---------|--------|
| `WHATSAPP_ENABLED` | `true` | |
| `TWILIO_ACCOUNT_SID` | (from Twilio) | |
| `TWILIO_AUTH_TOKEN` | (from Twilio) | |
| `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` | Sandbox or your number |
| `WHATSAPP_WEBHOOK_URL` | (base URL for webhook) | Optional |
| `STOREFRONT_URL` | `https://biasharahub-app-test.sysnovatechnologies.com` | For links in WhatsApp |

---

## Optional – OAuth2 (Google 2FA)

| Name | Example | Notes |
|------|---------|--------|
| `OAUTH2_BACKEND_BASE_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api` | Backend base URL |
| `OAUTH2_FRONTEND_REDIRECT_URI` | `https://biasharahub-app-test.sysnovatechnologies.com/auth/callback` | Frontend callback |

(Google client id/secret are often in `application-local.yml` or `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` if you add them.)

---

## Optional – Wallet / app

| Name | Example | Notes |
|------|---------|--------|
| `PLATFORM_COMMISSION_RATE` | `0.1` | 10% |
| `WALLET_MIN_PAYOUT_KES` | `10` | Min payout in KES |

---

## Optional – Kafka

| Name | Example | Notes |
|------|---------|--------|
| `KAFKA_ENABLED` | `false` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `KAFKA_CONSUMER_GROUP` | `biasharahub` | |
| `KAFKA_TOPIC_ORDER_CREATED` | `orders.created` | |
| `KAFKA_TOPIC_PAYMENT_COMPLETED` | `payments.completed` | |

---

## Optional – Courier APIs

| Name | Example | Notes |
|------|---------|--------|
| `COURIER_DHL_API_KEY` | (key) | If using DHL |
| `COURIER_FEDEX_API_KEY` | (key) | If using FedEx |
| `COURIER_SENDY_API_KEY` | (key) | If using Sendy |

---

## Optional – R2 (Cloudflare)

| Name | Example | Notes |
|------|---------|--------|
| `R2_ENABLED` | `false` | |
| `R2_BUCKET` | `biasharahub-product-images` | |
| `R2_ENDPOINT` | (R2 endpoint URL) | |
| `R2_ACCESS_KEY_ID` | (key) | |
| `R2_SECRET_ACCESS_KEY` | (secret) | |
| `R2_PUBLIC_URL` | (public base URL for objects) | |

---

## Optional – Encryption (production)

| Name | Example | Notes |
|------|---------|--------|
| `ENCRYPTION_SECRET` | (32-byte secret) | For encrypting sensitive fields; use in prod |

---

## Optional – Tenant schema

| Name | Example | Notes |
|------|---------|--------|
| `tenant.schema` | `tenant_default` | Default tenant schema (use Spring format: `TENANT_SCHEMA` may not bind; use system property or profile-specific yml if needed) |

---

## Summary

- **Required:** `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `APP_FRONTEND_URL`
- **In EB:** Configuration → Software → Edit → Environment properties → add each **Name** and **Value**
- **CORS:** Add the frontend origin in `SecurityConfig` (or wire `APP_CORS_ALLOWED_ORIGINS` into config) so the hosted frontend can call the API.
