# Environment variables – name and example (pair)

Use these in Elastic Beanstalk **Environment properties**: **Name** = first column, **Value** = second column.

---

## Required (UAT)

| Name | Example |
|------|--------|
| `SPRING_PROFILES_ACTIVE` | `test` |
| `SERVER_PORT` | `5000` |
| `DB_URL` | `jdbc:postgresql://a1b2c3d4eu-west1.rds.amazonaws.com:5432/biasharahub_test` |
| `DB_USERNAME` | `biasharaadmin` |
| `DB_PASSWORD` | `YourSecureRdsPassword123` |
| `JWT_SECRET` | `xK9mP2vL7nQ4wR8tY1bC6fE0aZ3dH5jS9u=` |
| `APP_FRONTEND_URL` | `https://biasharahub-app-test.sysnovatechnologies.com` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://biasharahub-app-test.sysnovatechnologies.com` |

---

## Required (Production)

| Name | Example |
|------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `5000` |
| `DB_URL` | `jdbc:postgresql://a1b2c3d4eu-west1.rds.amazonaws.com:5432/biasharahub` |
| `DB_USERNAME` | `biasharaadmin` |
| `DB_PASSWORD` | `YourSecureRdsPassword123` |
| `JWT_SECRET` | `differentSecretForProd256BitsBase64=` |
| `ENCRYPTION_SECRET` | `32-byte-secret-for-encryption!!` |
| `APP_FRONTEND_URL` | `https://biasharahub-app.sysnovatechnologies.com` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://biasharahub-app.sysnovatechnologies.com` |

---

## Optional – OAuth2 / callbacks

| Name | Example |
|------|--------|
| `OAUTH2_BACKEND_BASE_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api` |
| `OAUTH2_FRONTEND_REDIRECT_URI` | `https://biasharahub-app-test.sysnovatechnologies.com/auth/callback` |
| `STOREFRONT_URL` | `https://biasharahub-app-test.sysnovatechnologies.com` |

---

## Optional – Mail

| Name | Example |
|------|--------|
| `MAIL_FROM` | `noreply@sysnovatechnologies.com` |
| `MAIL_HOST` | `smtp.postmarkapp.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `your-postmark-token` |
| `MAIL_PASSWORD` | `your-postmark-token` |
| `POSTMARK_SERVER_TOKEN` | `your-postmark-server-token` |
| `POSTMARK_MESSAGE_STREAM` | `outbound` |

---

## Optional – Redis

| Name | Example |
|------|--------|
| `REDIS_HOST` | `biasharahub-uat.xxxxx.cache.amazonaws.com` |
| `REDIS_PORT` | `6379` |

---

## Optional – M-Pesa

| Name | Example |
|------|--------|
| `MPESA_ENABLED` | `true` |
| `MPESA_ENV` | `sandbox` |
| `MPESA_BASE_URL` | `https://sandbox.safaricom.co.ke` |
| `MPESA_SHORTCODE` | `174379` |
| `MPESA_PASSKEY` | `your-daraja-passkey` |
| `MPESA_CONSUMER_KEY` | `your-consumer-key` |
| `MPESA_CONSUMER_SECRET` | `your-consumer-secret` |
| `MPESA_STK_CALLBACK_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api/payments/mpesa/stk-callback` |
| `MPESA_B2C_CALLBACK_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api/payments/mpesa/b2c-callback` |
| `MPESA_B2C_INITIATOR_NAME` | `initiator_name` |
| `MPESA_B2C_SECURITY_CREDENTIAL` | `encrypted-credential` |
| `MPESA_B2C_SHORTCODE` | `174379` |
| `MPESA_TIMEOUT_MS` | `10000` |
| `MPESA_PAYOUT_PRODUCT_NAME` | `testapi` |

---

## Optional – WhatsApp (Twilio)

| Name | Example |
|------|--------|
| `WHATSAPP_ENABLED` | `true` |
| `TWILIO_ACCOUNT_SID` | `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` |
| `TWILIO_AUTH_TOKEN` | `your-twilio-auth-token` |
| `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` |
| `WHATSAPP_WEBHOOK_URL` | `https://biasharahub-api-test.sysnovatechnologies.com/api` |

---

## Optional – Wallet / app

| Name | Example |
|------|--------|
| `PLATFORM_COMMISSION_RATE` | `0.1` |
| `WALLET_MIN_PAYOUT_KES` | `10` |

---

## Optional – Kafka

| Name | Example |
|------|--------|
| `KAFKA_ENABLED` | `false` |
| `KAFKA_BOOTSTRAP_SERVERS` | `b-1.xxxxx.kafka.eu-west-1.amazonaws.com:9092` |
| `KAFKA_CONSUMER_GROUP` | `biasharahub` |
| `KAFKA_TOPIC_ORDER_CREATED` | `orders.created` |
| `KAFKA_TOPIC_PAYMENT_COMPLETED` | `payments.completed` |

---

## Optional – Courier APIs

| Name | Example |
|------|--------|
| `COURIER_DHL_API_KEY` | `your-dhl-api-key` |
| `COURIER_FEDEX_API_KEY` | `your-fedex-api-key` |
| `COURIER_SENDY_API_KEY` | `your-sendy-api-key` |

---

## Optional – R2 (Cloudflare)

| Name | Example |
|------|--------|
| `R2_ENABLED` | `false` |
| `R2_BUCKET` | `biasharahub-product-images` |
| `R2_ENDPOINT` | `https://xxx.r2.cloudflarestorage.com` |
| `R2_ACCESS_KEY_ID` | `your-r2-access-key` |
| `R2_SECRET_ACCESS_KEY` | `your-r2-secret-key` |
| `R2_PUBLIC_URL` | `https://images.sysnovatechnologies.com` |

---

## Optional – Other

| Name | Example |
|------|--------|
| `MESSAGING_IN_PROCESS_ENABLED` | `true` |

Replace RDS host, secrets, and API keys with your real values.
